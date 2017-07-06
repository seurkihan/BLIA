/**
 * Copyright (c) 2014 by Software Engineering Lab. of Sungkyunkwan University. All Rights Reserved.
 * 
 * Permission to use, copy, modify, and distribute this software and its documentation for
 * educational, research, and not-for-profit purposes, without fee and without a signed licensing agreement,
 * is hereby granted, provided that the above copyright notice appears in all copies, modifications, and distributions.
 */
package edu.skku.selab.blp.evaluation;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.skku.selab.blp.Property;
import edu.skku.selab.blp.common.Bug;
import edu.skku.selab.blp.common.SourceFile;
import edu.skku.selab.blp.db.ExperimentResult;
import edu.skku.selab.blp.db.IntegratedAnalysisValue;
import edu.skku.selab.blp.db.SourceFileExperimentResult;
import edu.skku.selab.blp.db.dao.BugDAO;
import edu.skku.selab.blp.db.dao.ExperimentResultDAO;
import edu.skku.selab.blp.db.dao.IntegratedAnalysisDAO;
import edu.skku.selab.blp.utils.Util;

/**
 * @author Klaus Changsun Youm(klausyoum@skku.edu)
 *
 */
public class Evaluator {
	public final static String ALG_BUG_LOCATOR = "BugLocator";
	public final static String ALG_BLIA_FILE = "BLIA_File";
	
	protected ExperimentResult experimentResult;
	protected ArrayList<SourceFileExperimentResult> sourceFileExperimentResultList;
	protected ArrayList<Bug> bugs = null;
	protected HashMap<Integer, HashSet<SourceFile>> realFixedFilesMap = null;;
	protected HashMap<Integer, ArrayList<IntegratedAnalysisValue>> rankedValuesMap = null;
	protected FileWriter writer = null; 
	
	protected Integer syncLock = 0;
	protected int top1 = 0;
	protected int top5 = 0;
	protected int top10 = 0;
	
	protected Double sumOfRRank = 0.0;
	protected Double MAP = 0.0;
	
	/**
	 * 
	 */
	public Evaluator(String productName, String algorithmName, String algorithmDescription,
			double alpha, double beta, double gamma, int pastDays) {
		experimentResult = new ExperimentResult();
		experimentResult.setProductName(productName);
		experimentResult.setAlgorithmName(algorithmName);
		experimentResult.setAlgorithmDescription(algorithmDescription);
		experimentResult.setAlpha(alpha);
		experimentResult.setBeta(beta);
		experimentResult.setGamma(gamma);
		experimentResult.setPastDays(pastDays);
		bugs = null;
		realFixedFilesMap = null;		
	}
	
	/**
	 * 
	 */
	public Evaluator(String productName, String algorithmName, String algorithmDescription,
			double alpha, double beta, double gamma, int pastDays, double candidateRate) {
		this(productName, algorithmName, algorithmDescription, alpha, beta, gamma, pastDays);
		experimentResult.setCandidateRate(candidateRate);
	}
	
	public void evaluate() throws Exception {
		long startTime = System.currentTimeMillis();
		System.out.printf("[STARTED] Evaluator.evaluate().\n");
		
		BugDAO bugDAO = new BugDAO();
		bugs = bugDAO.getAllBugs(true);
		
		realFixedFilesMap = new HashMap<Integer, HashSet<SourceFile>>();
		rankedValuesMap = new HashMap<Integer, ArrayList<IntegratedAnalysisValue>>();
		for (int i = 0; i < bugs.size(); i++) {
			int bugID = bugs.get(i).getID();
			HashSet<SourceFile> fixedFiles = bugDAO.getFixedFiles(bugID);
			realFixedFilesMap.put(bugID, fixedFiles);
			rankedValuesMap.put(bugID, getRankedValues(bugID, 0));
		}

		calculateMetrics();
		
		experimentResult.setExperimentDate(new Date(System.currentTimeMillis()));
		
		ExperimentResultDAO experimentResultDAO = new ExperimentResultDAO();
		experimentResultDAO.deleteSfExperimentResults(Property.getInstance().getProductName());
		
		experimentResultDAO.insertExperimentResult(experimentResult);
		
		if(Property.getInstance().isRefinedResultSave()){
			for(int i = 0; i<sourceFileExperimentResultList.size(); i++){
				SourceFileExperimentResult sourceFileExperimentResult = sourceFileExperimentResultList.get(i);
				sourceFileExperimentResult.setExperimentDate(experimentResult.getExperimentDate());
				sourceFileExperimentResultList.set(i, sourceFileExperimentResult);
		
				experimentResultDAO.insertSourceFileExperimentResult(sourceFileExperimentResult);
			}
		}
		
		
		System.out.printf("[DONE] Evaluator.evaluate().(Total %s sec)\n", Util.getElapsedTimeSting(startTime));
	}
	
	public ArrayList<Double> evaluate(ArrayList<Bug> bugList) throws Exception {
		long startTime = System.currentTimeMillis();
		//System.out.printf("[STARTED] Evaluator.evaluate().\n");
		
		BugDAO bugDAO = new BugDAO();
		bugs = bugDAO.getAllBugs(true);
		
		realFixedFilesMap = new HashMap<Integer, HashSet<SourceFile>>();
		rankedValuesMap = new HashMap<Integer, ArrayList<IntegratedAnalysisValue>>();
		for (int i = 0; i < bugs.size(); i++) {
			int bugID = bugs.get(i).getID();
			HashSet<SourceFile> fixedFiles = bugDAO.getFixedFiles(bugID);
			realFixedFilesMap.put(bugID, fixedFiles);
			rankedValuesMap.put(bugID, getRankedValues(bugID, 0));
		}

		ArrayList<Double> result = calculateMetrics(bugList);
		
		experimentResult.setExperimentDate(new Date(System.currentTimeMillis()));
		
		ExperimentResultDAO experimentResultDAO = new ExperimentResultDAO();
		experimentResultDAO.insertExperimentResult(experimentResult);
		
		if(Property.getInstance().isRefinedResultSave()){
			for(int i = 0; i<sourceFileExperimentResultList.size(); i++){
				SourceFileExperimentResult sourceFileExperimentResult = sourceFileExperimentResultList.get(i);
				sourceFileExperimentResult.setExperimentDate(experimentResult.getExperimentDate());
				sourceFileExperimentResultList.set(i, sourceFileExperimentResult);
		
				experimentResultDAO.insertSourceFileExperimentResult(sourceFileExperimentResult);
			}
		}
		
		
		//System.out.printf("[DONE] Evaluator.evaluate().(Total %s sec)\n", Util.getElapsedTimeSting(startTime));
		return result;
	}
	
	private ArrayList<IntegratedAnalysisValue> getRankedValues(int bugID, int limit) throws Exception {
		IntegratedAnalysisDAO integratedAnalysisDAO = new IntegratedAnalysisDAO();
		ArrayList<IntegratedAnalysisValue> rankedValues = null;
		if (experimentResult.getAlgorithmName().equalsIgnoreCase(Evaluator.ALG_BUG_LOCATOR)) {
			rankedValues = integratedAnalysisDAO.getBugLocatorRankedValues(bugID, limit);
		} else if (experimentResult.getAlgorithmName().equalsIgnoreCase(Evaluator.ALG_BLIA_FILE)) {
			rankedValues = integratedAnalysisDAO.getBliaSourceFileRankedValues(bugID, limit);
		}
		return rankedValues;
	}
	
    private class WorkerThread implements Runnable {
    	private int bugID;
        public WorkerThread(int bugID) {
            this.bugID = bugID;
        }
     
        @Override
        public void run() {
        	try {
        		calculateTopN();
        		calculateMRR();
        		calulateMAP();
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
        
        private void calculateTopN() throws Exception {
        	
			HashSet<SourceFile> realFixedFiles = realFixedFilesMap.get(bugID);
			// Exception handling
			if (null == realFixedFiles) {
				return;
			}

			HashSet<Integer> fixedFileVersionIDs = new HashSet<Integer>();
			HashMap<Integer, SourceFile> fixedFileVersionMap = new HashMap<Integer, SourceFile>();

			Iterator<SourceFile> fixedFilesIter = realFixedFiles.iterator();
			while (fixedFilesIter.hasNext()) {
				SourceFile fixedFile = fixedFilesIter.next();
				fixedFileVersionIDs.add(fixedFile.getSourceFileVersionID());
				fixedFileVersionMap.put(fixedFile.getSourceFileVersionID(), fixedFile);
			}
			
			int limitedCount = 10;
			
			// test code
			limitedCount = 30;
			boolean top1Bool = false;
			boolean top5Bool = false;
			boolean top10Bool = false;
			
			ArrayList<IntegratedAnalysisValue> rankedValues = rankedValuesMap.get(bugID);
			if (rankedValues == null) {
				System.err.printf("[ERROR] Bug ID: %d\n", bugID);
				return;
			}
			for (int j = 0; j < rankedValues.size(); j++) {
				int sourceFileVersionID = rankedValues.get(j).getSourceFileVersionID();
				if (fixedFileVersionIDs.contains(sourceFileVersionID)) {
			    	SourceFileExperimentResult sourceFileExperimentResult = new SourceFileExperimentResult();			    	
			    	sourceFileExperimentResult.setProductName(experimentResult.getProductName());
			    	sourceFileExperimentResult.setAlgorithmName(experimentResult.getAlgorithmName());
			    	sourceFileExperimentResult.setBugID(bugID);
			    	sourceFileExperimentResult.setAlpha(experimentResult.getAlpha());
			    	sourceFileExperimentResult.setBeta(experimentResult.getBeta());
			    	sourceFileExperimentResult.setGamma(experimentResult.getGamma());
			    	sourceFileExperimentResult.setPastDays(experimentResult.getPastDays());
					synchronized(syncLock) {
						if (j < 1) {
							if(top1Bool==false && top5Bool==false && top10Bool == false){
								top1++;
								top5++;
								top10++;
								top1Bool=true;
							}
						
							String log = bugID + " " + fixedFileVersionMap.get(sourceFileVersionID).getName() + " " + (j + 1) + "\n";
							writer.write(log);
							sourceFileExperimentResult.setFileName(fixedFileVersionMap.get(sourceFileVersionID).getName());
							sourceFileExperimentResult.setRank(j+1);
	//						System.out.printf("%d %s %d\n",
	//								bugID, fixedFileVersionMap.get(sourceFileVersionID).getName(), j + 1);
							//break;						
						} else if (j < 5) {
							if(top1Bool==false && top5Bool==false && top10Bool == false){
								top5++;
								top10++;
								top5Bool=true;
							}
							
							String log = bugID + " " + fixedFileVersionMap.get(sourceFileVersionID).getName() + " " + (j + 1) + "\n";
							writer.write(log);

							sourceFileExperimentResult.setFileName(fixedFileVersionMap.get(sourceFileVersionID).getName());
							sourceFileExperimentResult.setRank(j+1);
	//						System.out.printf("%d %s %d\n",
	//								bugID, fixedFileVersionMap.get(sourceFileVersionID).getName(), j + 1);
							//break;
						} else if (j < 10) {
							if(top1Bool==false && top5Bool==false && top10Bool == false){
								top10++;
								top10Bool=true;
							}

							String log = bugID + " " + fixedFileVersionMap.get(sourceFileVersionID).getName() + " " + (j + 1) + "\n";
							writer.write(log);

							sourceFileExperimentResult.setFileName(fixedFileVersionMap.get(sourceFileVersionID).getName());
							sourceFileExperimentResult.setRank(j+1);
	//						System.out.printf("%d %s %d\n",
	//								bugID, fixedFileVersionMap.get(sourceFileVersionID).getName(), j + 1);
							//break;
						}
						// debug code
						else if (j < limitedCount) {
							String log = bugID + " " + fixedFileVersionMap.get(sourceFileVersionID).getName() + " " + (j + 1) + "\n";
							writer.write(log);

							sourceFileExperimentResult.setFileName(fixedFileVersionMap.get(sourceFileVersionID).getName());
							sourceFileExperimentResult.setRank(j+1);
	//						System.out.printf("%d %s %d\n",
	//								bugID, fixedFileVersionMap.get(sourceFileVersionID).getName(), j + 1);
							//break;
						}// debug code
						else{
							String log = bugID + " " + fixedFileVersionMap.get(sourceFileVersionID).getName() + " " + (j + 1) + "\n";
							writer.write(log);

							sourceFileExperimentResult.setFileName(fixedFileVersionMap.get(sourceFileVersionID).getName());
							sourceFileExperimentResult.setRank(j+1);
	//						System.out.printf("%d %s %d\n",
	//								bugID, fixedFileVersionMap.get(sourceFileVersionID).getName(), j + 1);
							//break;
						}
					}
					sourceFileExperimentResultList.add(sourceFileExperimentResult);
				}
			}
        }
        
        private void calculateMRR() throws Exception {
			HashSet<SourceFile> fixedFiles = realFixedFilesMap.get(bugID);
			// Exception handling
			if (null == fixedFiles) {
				return;
			}

			HashSet<Integer> fixedFileVersionIDs = new HashSet<Integer>();
			Iterator<SourceFile> fixedFilesIter = fixedFiles.iterator();
			while (fixedFilesIter.hasNext()) {
				SourceFile fixedFile = fixedFilesIter.next();
				fixedFileVersionIDs.add(fixedFile.getSourceFileVersionID());
			}
			
			ArrayList<IntegratedAnalysisValue> rankedValues = rankedValuesMap.get(bugID);
			if (rankedValues == null) {
				System.err.printf("[ERROR] Bug ID: %d\n", bugID);
				return;
			}
			for (int j = 0; j < rankedValues.size(); j ++) {
				int sourceFileVersionID = rankedValues.get(j).getSourceFileVersionID();
				
				if (fixedFileVersionIDs.contains(sourceFileVersionID)) {
//					System.out.printf("BugID: %s, Rank: %d\n", bugID, j + 1);
					synchronized(sumOfRRank) {
						sumOfRRank += (1.0 / (j + 1));
					}
					break;
				}
			}
        }
        
        private void calulateMAP() throws Exception {
        	double AP = 0;
        	
			HashSet<SourceFile> fixedFiles = realFixedFilesMap.get(bugID);
			// Exception handling
			if (null == fixedFiles) {
				return;
			}

			HashSet<Integer> fixedFileVersionIDs = new HashSet<Integer>();
			Iterator<SourceFile> fixedFilesIter = fixedFiles.iterator();
			while (fixedFilesIter.hasNext()) {
				SourceFile fixedFile = fixedFilesIter.next();
				fixedFileVersionIDs.add(fixedFile.getSourceFileVersionID());
			}
			
			int numberOfFixedFiles = 0;
			int numberOfPositiveInstances = 0;
			ArrayList<IntegratedAnalysisValue> rankedValues = rankedValuesMap.get(bugID);
			if (rankedValues == null) {
				System.err.printf("[ERROR] Bug ID: %d\n", bugID);
				return;
			}
			for (int j = 0; j < rankedValues.size(); j++) {
				int sourceFileVersionID = rankedValues.get(j).getSourceFileVersionID();
				if (fixedFileVersionIDs.contains(sourceFileVersionID)) {
					numberOfPositiveInstances++;
				}
			}

			double precision = 0.0;
			for (int j = 0; j < rankedValues.size(); j++) {
				int sourceFileVersionID = rankedValues.get(j).getSourceFileVersionID();
				if (fixedFileVersionIDs.contains(sourceFileVersionID)) {
					numberOfFixedFiles++;
					precision = ((double) numberOfFixedFiles) / (j + 1);
					AP += (precision / numberOfPositiveInstances);
				}
			}
			
			synchronized(MAP) {
//				System.out.printf("[LOG]\t%d\t%f\n", bugID, AP);
				MAP += AP;
			}
        }
    }
    
    protected String getOutputFileName() {
		String outputFileName = String.format("D:\\Users\\rose\\BLIA\\Results\\%s_alpha_%.1f_beta_%.1f_gamma_%.1f_k_%d",
				experimentResult.getProductName(), experimentResult.getAlpha(), experimentResult.getBeta(),
				experimentResult.getGamma(), experimentResult.getPastDays()); 
    	/*String outputFileName = String.format("D:\\Users\\rose\\BLIA\\Results\\%s-dc",
				experimentResult.getProductName()); */
		if (experimentResult.getCandidateRate() > 0.0) {
			outputFileName += String.format("_cand_rate_%.2f", experimentResult.getCandidateRate()); 			
		}
		outputFileName += "_" + experimentResult.getAlgorithmName() + ".csv";
		
		return outputFileName;
    }
	
	protected void calculateMetrics() throws Exception {
		String outputFileName = getOutputFileName();
		writer = new FileWriter(outputFileName, false);
		
		ExecutorService executor = Executors.newFixedThreadPool(Property.THREAD_COUNT);
		sourceFileExperimentResultList = new ArrayList<SourceFileExperimentResult>();
//		boolean isCounted = false;
		for (int i = 0; i < bugs.size(); i++) {
			Runnable worker = new WorkerThread(bugs.get(i).getID());
			executor.execute(worker);
		}
		
		executor.shutdown();
		while (!executor.isTerminated()) {
		}
		
		experimentResult.setTop1(top1);
		experimentResult.setTop5(top5);
		experimentResult.setTop10(top10);
		
		int bugCount = bugs.size();
		experimentResult.setTop1Rate((double) top1 / bugCount);
		experimentResult.setTop5Rate((double) top5 / bugCount);
		experimentResult.setTop10Rate((double) top10 / bugCount);

		System.out.printf("[%s] Top1: %d, Top5: %d, Top10: %d, Top1Rate: %f, Top5Rate: %f, Top10Rate: %f\n",
				experimentResult.getAlgorithmName(),
				experimentResult.getTop1(), experimentResult.getTop5(), experimentResult.getTop10(),
				experimentResult.getTop1Rate(), experimentResult.getTop5Rate(), experimentResult.getTop10Rate());
		String log = "Top1: " + experimentResult.getTop1() + ", " +
				"Top5: " + experimentResult.getTop5() + ", " +
				"Top10: " + experimentResult.getTop10() + ", " +
				"Top1Rate: " + experimentResult.getTop1Rate() + ", " +
				"Top5Rate: " + experimentResult.getTop5Rate() + ", " +
				"Top10Rate: " + experimentResult.getTop10Rate() + "\n";
		writer.write(log);
		
////////////////////////////////////////////////////////////////////////////
		double MRR = sumOfRRank / bugs.size();
		experimentResult.setMRR(MRR);
		
		System.out.printf("MRR: %f\n", experimentResult.getMRR());
		log = "MRR: " + experimentResult.getMRR() + "\n";
		writer.write(log);

////////////////////////////////////////////////////////////////////////////
		MAP = MAP / bugs.size();
		experimentResult.setMAP(MAP);
		
		System.out.printf("MAP: %f\n", experimentResult.getMAP());
		log = "MAP: " + experimentResult.getMAP() + "\n";
		writer.write(log);
		
		writer.flush();
		writer.close();
	}
	
	protected ArrayList<Double> calculateMetrics(ArrayList<Bug> bugList) throws Exception {
		ArrayList<Double> result = new ArrayList<Double>();
		String outputFileName = getOutputFileName();
		writer = new FileWriter(outputFileName, false);
		
		ExecutorService executor = Executors.newFixedThreadPool(Property.THREAD_COUNT);
		sourceFileExperimentResultList = new ArrayList<SourceFileExperimentResult>();
//		boolean isCounted = false;
		for (int i = 0; i < bugs.size(); i++) {
			Runnable worker = new WorkerThread(bugs.get(i).getID());
			executor.execute(worker);
		}
		
		executor.shutdown();
		while (!executor.isTerminated()) {
		}
		
		experimentResult.setTop1(top1);
		experimentResult.setTop5(top5);
		experimentResult.setTop10(top10);
		
		int bugCount = bugs.size();
		experimentResult.setTop1Rate((double) top1 / bugCount);
		experimentResult.setTop5Rate((double) top5 / bugCount);
		experimentResult.setTop10Rate((double) top10 / bugCount);

//		System.out.printf("[%s] Top1: %d, Top5: %d, Top10: %d, Top1Rate: %f, Top5Rate: %f, Top10Rate: %f\n",
//				experimentResult.getAlgorithmName(),
//				experimentResult.getTop1(), experimentResult.getTop5(), experimentResult.getTop10(),
//				experimentResult.getTop1Rate(), experimentResult.getTop5Rate(), experimentResult.getTop10Rate());
		String log = "Top1: " + experimentResult.getTop1() + ", " +
				"Top5: " + experimentResult.getTop5() + ", " +
				"Top10: " + experimentResult.getTop10() + ", " +
				"Top1Rate: " + experimentResult.getTop1Rate() + ", " +
				"Top5Rate: " + experimentResult.getTop5Rate() + ", " +
				"Top10Rate: " + experimentResult.getTop10Rate() + "\n";
		writer.write(log);
		
////////////////////////////////////////////////////////////////////////////
		double MRR = sumOfRRank / bugs.size();
		experimentResult.setMRR(MRR);
		
//		System.out.printf("MRR: %f\n", experimentResult.getMRR());
		log = "MRR: " + experimentResult.getMRR() + "\n";
		writer.write(log);

////////////////////////////////////////////////////////////////////////////
		MAP = MAP / bugs.size();
		experimentResult.setMAP(MAP);
		
//		System.out.printf("MAP: %f\n", experimentResult.getMAP());
		log = "MAP: " + experimentResult.getMAP() + "\n";
		writer.write(log);
		
		writer.flush();
		writer.close();
		
		System.out.println("["+experimentResult.getAlgorithmName()+"]\t Top1R: "+experimentResult.getTop1Rate()+", Top5R: "+experimentResult.getTop5Rate()+
				", Top10R:"+experimentResult.getTop10Rate()+", MAP: "+experimentResult.getMAP()+", MRR: "+ experimentResult.getMRR());
		
		result.add(MAP);
		result.add(MRR);
		
		return result;
	}
}
