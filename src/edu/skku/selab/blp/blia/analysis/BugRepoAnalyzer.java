/**
 * Copyright (c) 2014 by Software Engineering Lab. of Sungkyunkwan University. All Rights Reserved.
 * 
 * Permission to use, copy, modify, and distribute this software and its documentation for
 * educational, research, and not-for-profit purposes, without fee and without a signed licensing agreement,
 * is hereby granted, provided that the above copyright notice appears in all copies, modifications, and distributions.
 */
package edu.skku.selab.blp.blia.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import edu.skku.selab.blp.Property;
import edu.skku.selab.blp.common.Bug;
import edu.skku.selab.blp.common.SourceFile;
import edu.skku.selab.blp.db.AnalysisValue;
import edu.skku.selab.blp.db.SimilarBugInfo;
import edu.skku.selab.blp.db.dao.BugDAO;
import edu.skku.selab.blp.db.dao.IntegratedAnalysisDAO;

/**
 * @author Klaus Changsun Youm(klausyoum@skku.edu)
 *
 */
public class BugRepoAnalyzer {
    public BugRepoAnalyzer() {
    }

	/**
	 * Analyze similarity between a bug report and its previous bug reports. Then write similarity scores to SimiScore.txt
	 * ex.) Bug ID; Target bug ID#1:Similarity score	Target big ID#2:Similarity score 
	 * 
	 * (non-Javadoc)
	 * @see edu.skku.selab.blp.analysis.IAnalyzer#analyze()
	 */
	public void analyze() throws Exception {
		computeSimilarity();

		Property property = Property.getInstance();
		String productName = property.getProductName();
		BugDAO bugDAO = new BugDAO();
		IntegratedAnalysisDAO integratedAnalysisDAO = new IntegratedAnalysisDAO();
		ArrayList<Bug> bugs = bugDAO.getBugs(productName, true);
		
		
		for (int i = 0; i < bugs.size(); i++) {
			Bug bug = bugs.get(i);
			String bugID = bug.getID();
			HashMap<Integer, Double> similarScores = new HashMap<Integer, Double>(); 
			HashSet<SimilarBugInfo> similarBugInfos = bugDAO.getSimilarBugInfos(bugID);
			if (null != similarBugInfos) {
				Iterator<SimilarBugInfo> similarBugInfosIter = similarBugInfos.iterator();
				while (similarBugInfosIter.hasNext()) {
					SimilarBugInfo similarBugInfo = similarBugInfosIter.next();
					
					HashSet<SourceFile> fixedFiles = bugDAO.getFixedFiles(similarBugInfo.getSimilarBugID());
					if (null != fixedFiles) {
						int fixedFilesCount = fixedFiles.size();
						double singleValue = similarBugInfo.getSimilarityScore() / fixedFilesCount;
						Iterator<SourceFile> fixedFilesIter = fixedFiles.iterator();
						while (fixedFilesIter.hasNext()) {
							SourceFile fixedFile = fixedFilesIter.next();
							
							int sourceFileVersionID = fixedFile.getSourceFileVersionID();
							if (null != similarScores.get(sourceFileVersionID)) {
								double similarScore = similarScores.get(sourceFileVersionID).doubleValue() + singleValue;
								similarScores.remove(sourceFileVersionID);
								similarScores.put(sourceFileVersionID, Double.valueOf(similarScore));
							} else {
								similarScores.put(sourceFileVersionID, Double.valueOf(singleValue));
							}
						}				
					}
				}
				
				Iterator<Integer> similarScoresIter = similarScores.keySet().iterator();
				while (similarScoresIter.hasNext()) {
					int sourceFileVersionID = similarScoresIter.next();
					double similarScore = similarScores.get(sourceFileVersionID).doubleValue();
					
					if (similarScore != 0.0) {
						integratedAnalysisDAO.updateSimilarScore(bugID, sourceFileVersionID, similarScore);
					}
				}
			}
		}
	}
	
	public void computeSimilarity() throws Exception {
		Property property = Property.getInstance();
		String productName = property.getProductName();
		BugDAO bugDAO = new BugDAO();
		ArrayList<Bug> bugs = bugDAO.getBugs(productName, true);
		
		HashMap<String, ArrayList<AnalysisValue>> bugVectors = getVector();
		
        for(int i = 0; i < bugs.size(); i++) {
        	String firstBugID = bugs.get(i).getID();
        	ArrayList<AnalysisValue> firstBugVector = bugVectors.get(firstBugID);
        	
            for(int j = 0; j < i; j++) {
            	String secondBugID = bugs.get(j).getID();
            	ArrayList<AnalysisValue> secondBugVector = bugVectors.get(secondBugID);
            	
            	double similarityScore = getCosineValue(firstBugVector, secondBugVector);
            	
            	bugDAO.insertSimilarBugInfo(firstBugID, secondBugID, similarityScore);
            }
        }
	}
	
	/**
	 * Get cosine value from two vectors
	 * 
	 * @param firstVector
	 * @param secondVector
	 * @return
	 */
	private double getCosineValue(ArrayList<AnalysisValue> firstBugVector, ArrayList<AnalysisValue> secondBugVector) throws Exception {
		double len1 = 0.0;
		double len2 = 0.0;
		double product = 0.0;
		
		TreeSet<Integer> corpusIDSet = new TreeSet<Integer>();
		int corpusID = -1;
		for (int i = 0; i < firstBugVector.size(); i++) {
			corpusID = firstBugVector.get(i).getCorpusID();
			corpusIDSet.add(corpusID);
		}
		
		for (int i = 0; i < secondBugVector.size(); i++) {
			corpusID = secondBugVector.get(i).getCorpusID();
			corpusIDSet.add(corpusID);
		}
		
		double firstBugVectorValue[] = new double[corpusIDSet.size()];
		double secondBugVectorValue[] = new double[corpusIDSet.size()];

		int i = 0;
		int j = 0;
		int k = 0;
		Iterator<Integer> corpusIDSetIter = corpusIDSet.iterator();
		while (corpusIDSetIter.hasNext()) {
			corpusID = corpusIDSetIter.next();
			if (j < firstBugVector.size() && corpusID == firstBugVector.get(j).getCorpusID()) {
				firstBugVectorValue[i] = firstBugVector.get(j).getVector();
				j++;
			}
			
			if (k < secondBugVector.size() && corpusID == secondBugVector.get(k).getCorpusID()) {
				secondBugVectorValue[i] = secondBugVector.get(k).getVector();
				k++;
			}
			i++;
		}
		
		for (i = 0; i < corpusIDSet.size(); i++) {
			len1 += firstBugVectorValue[i] * firstBugVectorValue[i];
			len2 += secondBugVectorValue[i] * secondBugVectorValue[i];
			product += firstBugVectorValue[i] * secondBugVectorValue[i];
		}
				
		return ((double) product / (Math.sqrt(len1) * Math.sqrt(len2)));
	}
	
	/**
	 * Get bug vector value 
	 * 
	 * @return <bug ID, <Corpus ID, AnalysisValue>> 
	 * @throws IOException
	 */
	public HashMap<String, ArrayList<AnalysisValue>> getVector() throws Exception {
		Property property = Property.getInstance();
		String productName = property.getProductName();
		HashMap<String, ArrayList<AnalysisValue>> bugVectors = new HashMap<String, ArrayList<AnalysisValue>>();
		
		BugDAO bugDAO = new BugDAO();
		ArrayList<Bug> bugs = bugDAO.getBugs(productName, true);
		
		for (int i = 0; i < bugs.size(); i++) {
			String bugID = bugs.get(i).getID();
			bugVectors.put(bugID, bugDAO.getBugAnalysisValues(bugID));			
		}
		
		return bugVectors;
	}
}