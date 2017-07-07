/**
 * Copyright (c) 2014 by Software Engineering Lab. of Sungkyunkwan University. All Rights Reserved.
 * 
 * Permission to use, copy, modify, and distribute this software and its documentation for
 * educational, research, and not-for-profit purposes, without fee and without a signed licensing agreement,
 * is hereby granted, provided that the above copyright notice appears in all copies, modifications, and distributions.
 */
package edu.skku.selab.blp.blia.indexer;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import edu.skku.selab.blp.Property;
import edu.skku.selab.blp.common.SourceFileCorpus;
import edu.skku.selab.blp.common.FileDetector;
import edu.skku.selab.blp.common.FileParser;
import edu.skku.selab.blp.db.dao.BaseDAO;
import edu.skku.selab.blp.db.dao.SourceFileDAO;
import edu.skku.selab.blp.utils.ContractionsExpansor;
import edu.skku.selab.blp.utils.Inflector;
import edu.skku.selab.blp.utils.Lemmatization;
import edu.skku.selab.blp.utils.Stem;
import edu.skku.selab.blp.utils.Stopword;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * @author Klaus Changsun Youm(klausyoum@skku.edu)
 *
 */
public class SourceFileCorpusCreator {
	// 20170707 - Extend Preprocessing by Misoo Rose
	// 20170707 - Singularization + Expansion + Remove 1 tokens + Remove Number +  Noun / Verb Terms
	// 20170707 - Noun / Verb terms = Better Top10, MAP/MRR/top1,5 is little bit reduced
	
	static Inflector inflector = new Inflector();
	private static ContractionsExpansor contExpans = new ContractionsExpansor();
	static MaxentTagger tagger= new MaxentTagger("E:\\Git-Project\\Stanford-POS\\taggers\\english-left3words-distsim.tagger");
	
	
	public static String stemContent(String contents[]) {
		StringBuffer contentBuf = new StringBuffer();
		
		
		for (int i = 0; i < contents.length; i++) {
			String word = contents[i].toLowerCase();
			if(Property.getInstance().isNewPreprocessing()){
				if(!Property.getInstance().getProductName().equalsIgnoreCase("aspectj"))
					word = inflector.singularize(word);
				if (word.length() > 1) {
					String[] temp = contExpans.expand(word).split(" ");
					
					for(int j = 0 ; j<temp.length; j++){
						if (temp[j].length() <= 1)						
							continue;
						
						String tagged = tagger.tagString(temp[j]);
						if(!tagged.split("_")[1].contains("N") && !tagged.split("_")[1].contains("V") 
								 && !tagged.split("_")[1].contains("SYM") &&  !tagged.split("_")[1].contains("JJ")
								 &&  !tagged.split("_")[1].contains("RB"))
							continue;

						if(tagged.split("_")[1].contains("RBR") || tagged.split("_")[1].contains("RBS")
								|| tagged.split("_")[1].contains("JJR") || tagged.split("_")[1].contains("JJS"))
							continue;
						
						String stemWord = Stem.stem(temp[j]);
	//					String stemWord = Stem.stem(word);
						
						
						if (!Stopword.isJavaKeyword(stemWord) && !Stopword.isProjectKeyword(stemWord) 
								&& !Stopword.isEnglishStopword(stemWord)&& !Stopword.isJEmotionalword(stemWord)) {
							try{
								Integer.parseInt(stemWord);
							}catch(Exception e){
								contentBuf.append(stemWord);
								contentBuf.append(" ");
							}				
						}
					}
				}
			}else{
				if (word.length() < 1)
					continue;
				String stemWord = Stem.stem(word);
				if (!Stopword.isJavaKeyword(stemWord) && !Stopword.isProjectKeyword(stemWord)
						&& !Stopword.isEnglishStopword(stemWord)) {
					try{
						Integer.parseInt(stemWord);
					}catch(Exception e){
						contentBuf.append(stemWord);
						contentBuf.append(" ");
					}				
				}
			}
		}
		return contentBuf.toString();
	}
	
	public static String stemContent(String content) {
		StringBuffer contentBuf = new StringBuffer();
		String word = content.toLowerCase();
	
		String stemWord = Stem.stem(word);
		if (!Stopword.isJavaKeyword(stemWord) && !Stopword.isProjectKeyword(stemWord)&& !Stopword.isEnglishStopword(stemWord)) {
			contentBuf.append(stemWord);
			contentBuf.append(" ");
		}
		
		
		return contentBuf.toString();
	}

	
	public SourceFileCorpus create(File file) {
		FileParser parser = new FileParser(file);
		String fileName = parser.getPackageName();
		if (fileName.trim().equals("")) {
			fileName = file.getName();
		} else {
			fileName = (new StringBuilder(String.valueOf(fileName)))
					.append(".").append(file.getName()).toString();
		}
		fileName = fileName.substring(0, fileName.lastIndexOf("."));
		
		// parser.getImportedClasses() function should be called before calling parser.getContents()
		ArrayList<String> importedClasses = parser.getImportedClasses();
		String content[] = parser.getContent();
				
		String sourceCodeContent = stemContent(content);
		
		// 20170707 - Extend Preprocessing for remove less than 10 characters
		SourceFileCorpus corpus = null;
		if(content.length < 5) return corpus;
		
		String classNameAndMethodName[] = parser.getClassNameAndMethodName();
		String names = stemContent(classNameAndMethodName);
		corpus = new SourceFileCorpus();
		corpus.setJavaFilePath(file.getAbsolutePath());
		corpus.setJavaFileFullClassName(fileName);
		corpus.setContent((new StringBuilder(String.valueOf(sourceCodeContent)))
				.append(" ").append(names).toString());
		corpus.setImportedClasses(importedClasses);
		return corpus;
    }
	
	////////////////////////////////////////////////////////////////////	
	/* (non-Javadoc)
	 * @see edu.skku.selab.blia.indexer.ICorpus#create()
	 */
	public void create(String version) throws Exception {
		Property property = Property.getInstance();
		FileDetector detector = new FileDetector("java");
		File files[] = detector.detect(property.getSourceCodeDirList());
		
		SourceFileDAO sourceFileDAO = new SourceFileDAO();
		int count = 0;
		TreeSet<String> nameSet = new TreeSet<String>();
		for (int i = 0; i < files.length; i++) {
			File file = files[i];
			SourceFileCorpus corpus = create(file);

			// 20170707 - Extend Preprocessing for remove less than 10 characters
			if(corpus == null) continue;
			
			if (corpus != null && !nameSet.contains(corpus.getJavaFileFullClassName())) {
				String fileName = corpus.getJavaFileFullClassName();
				if (!corpus.getJavaFileFullClassName().endsWith(".java")) {
					fileName += ".java";
				}

				int sourceFileID = sourceFileDAO.insertSourceFile(fileName);
				if (BaseDAO.INVALID == sourceFileID) {
					System.err.printf("[StructuredSourceFileCorpusCreator.create()] %s insertSourceFile() failed.\n", fileName);
					throw new Exception(); 
				}
				
				int sourceFileVersionID = sourceFileDAO.insertCorpusSet(sourceFileID, version, corpus,
						SourceFileDAO.INIT_TOTAL_COUPUS_COUNT, SourceFileDAO.INIT_LENGTH_SCORE);
				if (BaseDAO.INVALID == sourceFileVersionID) {
					System.err.printf("[StructuredSourceFileCorpusCreator.create()] %s insertCorpusSet() failed.\n", fileName);
					throw new Exception(); 
				}

				sourceFileDAO.insertImportedClasses(sourceFileVersionID, corpus.getImportedClasses());
				nameSet.add(corpus.getJavaFileFullClassName());
				count++;
			}
		}

		property.setFileCount(count);
	}

	//20170707 - Implement commit based Just diff. files creator
	public void create(String version, ArrayList<String> fileList) throws Exception {
		Property property = Property.getInstance();
		FileDetector detector = new FileDetector("java");
		File files[] = detector.detect(property.getSourceCodeDirList());
		
		SourceFileDAO sourceFileDAO = new SourceFileDAO();
		int count = 0;
		TreeSet<String> nameSet = new TreeSet<String>();
		for (int i = 0; i < files.length; i++) {
			File file = files[i];
			
			boolean stopFlag = false;
			for(int j = 0 ; j<fileList.size(); j++){				
				String diffFile = fileList.get(j).replace("MODIFY ", "").replace("ADD ", "").replace("DELETE ", "");
				if(file.toString().toLowerCase().contains(diffFile.toLowerCase())){
					stopFlag = true;
					break;
				}
			}
			if(!stopFlag) continue;
			
			
			SourceFileCorpus corpus = create(file);
			if (corpus != null && !nameSet.contains(corpus.getJavaFileFullClassName())) {
				String fileName = corpus.getJavaFileFullClassName();
				if (!corpus.getJavaFileFullClassName().endsWith(".java")) {
					fileName += ".java";
				}

				int sourceFileID = sourceFileDAO.insertSourceFile(fileName);
				if (BaseDAO.INVALID == sourceFileID) {
					System.err.printf("[StructuredSourceFileCorpusCreator.create()] %s insertSourceFile() failed.\n", fileName);
					throw new Exception(); 
				}
				
				int sourceFileVersionID = sourceFileDAO.insertCorpusSet(sourceFileID, version, corpus,
						SourceFileDAO.INIT_TOTAL_COUPUS_COUNT, SourceFileDAO.INIT_LENGTH_SCORE);
				if (BaseDAO.INVALID == sourceFileVersionID) {
					System.err.printf("[StructuredSourceFileCorpusCreator.create()] %s insertCorpusSet() failed.\n", fileName);
					throw new Exception(); 
				}

				sourceFileDAO.insertImportedClasses(sourceFileVersionID, corpus.getImportedClasses());
				nameSet.add(corpus.getJavaFileFullClassName());
				count++;
			}
		}

		property.setFileCount(count);
	}
}
