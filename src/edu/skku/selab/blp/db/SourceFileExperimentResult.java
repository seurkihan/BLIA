/**
 * Copyright (c) 2014 by Software Engineering Lab. of Sungkyunkwan University. All Rights Reserved.
 * 
 * Permission to use, copy, modify, and distribute this software and its documentation for
 * educational, research, and not-for-profit purposes, without fee and without a signed licensing agreement,
 * is hereby granted, provided that the above copyright notice appears in all copies, modifications, and distributions.
 */
package edu.skku.selab.blp.db;

import java.text.SimpleDateFormat;
import java.util.Date;

import edu.skku.selab.blp.evaluation.Evaluator;

/**
 * @author Klaus Changsun Youm(klausyoum@skku.edu)
 *
 */
public class SourceFileExperimentResult {	
	private String productName;
	private String algorithmName;
	private Integer bugID;
	private String fileName;
	private int rank;
	private double alpha;
	private double beta;
	private double gamma;
	private int pastDays;
	private Date experimentDate;
	
	/**
	 * 
	 */
	public SourceFileExperimentResult() {
		bugID = 0;
		productName = "";
		algorithmName = "";
		fileName = "";
		alpha = 0.0;
		beta = 0.0;
		gamma = 0.0;
		rank = 0;
		pastDays = 0;
		setExperimentDate(new Date(System.currentTimeMillis()));
	}


	/**
	 * @return the productName
	 */
	public String getProductName() {
		return productName;
	}

	/**
	 * @param productName the productName to set
	 */
	public void setProductName(String productName) {
		this.productName = productName;
	}

	/**
	 * @return the algorithmName
	 */
	public String getAlgorithmName() {
		return algorithmName;
	}

	/**
	 * @param algorithmName the algorithmName to set
	 */
	public void setAlgorithmName(String algorithmName) {
		this.algorithmName = algorithmName;
	}
	

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
	}
	
	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	/**
	 * @return the experimentDate
	 */
	public Date getExperimentDate() {
		return experimentDate;
	}

	/**
	 * @param experimentDate the experimentDate to set
	 */
	public void setExperimentDate(Date experimentDate) {
		this.experimentDate = experimentDate;
	}

	/**
	 * @return the experimentDateString
	 */
	public String getExperimentDateString() {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return simpleDateFormat.format(experimentDate);
	}

	/**
	 * @param experimentDateString the experimentDateString to set
	 */
	public void setExperimentDate(String experimentDateString) {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		try {
			this.experimentDate = simpleDateFormat.parse(experimentDateString);			
		} catch (Exception e) {
			this.experimentDate = null;
			e.printStackTrace();
		}		
	}

	
	/**
	 * @return the alpha
	 */
	public double getAlpha() {
		return alpha;
	}

	/**
	 * @param alpha the alpha to set
	 */
	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

	/**
	 * @return the beta
	 */
	public double getBeta() {
		return beta;
	}

	/**
	 * @param beta the beta to set
	 */
	public void setBeta(double beta) {
		this.beta = beta;
	}

	/**
	 * @return the gamma
	 */
	public double getGamma() {
		return gamma;
	}

	/**
	 * @param gamma the gamma to set
	 */
	public void setGamma(double gamma) {
		this.gamma = gamma;
	}


	public int getPastDays() {
		return pastDays;
	}


	public void setPastDays(int pastDays) {
		this.pastDays = pastDays;
	}


	public Integer getBugID() {
		return bugID;
	}


	public void setBugID(Integer bugID) {
		this.bugID = bugID;
	}

}
