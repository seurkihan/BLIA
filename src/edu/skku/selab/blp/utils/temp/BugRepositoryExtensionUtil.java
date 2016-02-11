/**
 * Copyright (c) 2014 by Software Engineering Lab. of Sungkyunkwan University. All Rights Reserved.
 * 
 * Permission to use, copy, modify, and distribute this software and its documentation for
 * educational, research, and not-for-profit purposes, without fee and without a signed licensing agreement,
 * is hereby granted, provided that the above copyright notice appears in all copies, modifications, and distributions.
 */
package edu.skku.selab.blp.utils.temp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.skku.selab.blp.common.Method;
import edu.skku.selab.blp.utils.temp.MethodVisitor;

/**
 * @author Jun Ahn(ahnjune@skku.edu)
 * @author Klaus Changsun Youm(klausyoum@skku.edu)
 *
 */
public class BugRepositoryExtensionUtil {
	private static HashMap<String, HashSet<String>> fixedCommitMap = new HashMap<String, HashSet<String>>();
	private static HashMap<String, RevCommit> allCommitMap = new HashMap<String, RevCommit>();
	
//	private static String FIXED_COMMIT_FILE = "AspectJFixedCommits.txt";
//	private static String FIXED_COMMIT_FILE = "SWTFixedCommits.txt";
	private static String FIXED_COMMIT_FILE = "ZXingFixedCommits.txt";
	
//	private static String PROJECT_GIT_PATH = "/git/org.aspectj/.git";
//	private static String PROJECT_GIT_PATH = "/git/eclipse.platform.swt/.git";
	private static String PROJECT_GIT_PATH = "/git/zxing/.git";
	
//	private static String TARGET_PRODUCT_NAME = "AspectJ";
//	private static String TARGET_PRODUCT_NAME = "SWT";
	private static String TARGET_PRODUCT_NAME = "ZXing";

	private static CompilationUnit getCompilationUnit(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
		parser.setCompilerOptions(options);
		parser.setSource(source.toCharArray());
		CompilationUnit cu = (CompilationUnit)parser.createAST(null);

		return cu;
	}
	
	private static void loadFixedCommits() throws FileNotFoundException {
		String userHomeDir = System.getProperty("user.home");
		String bugRepoPath = userHomeDir + "/git/BLIA/data/";
		Scanner bugRepoRead = new Scanner(new File(bugRepoPath + FIXED_COMMIT_FILE));

		while (bugRepoRead.hasNextLine()) {
			String line = bugRepoRead.nextLine();
			String[] items = line.split(" ");

			String bugID = items[0];
			HashSet<String> hashSet = null;
			for (int i = 1; i < items.length; i++) {
				hashSet = fixedCommitMap.get(bugID);
				if (hashSet == null) {
					hashSet = new HashSet<String>();
				}

				hashSet.add(items[i]);
			}

			fixedCommitMap.put(bugID, hashSet);
		}
	}
	
	private static void loadAllCommits() throws IOException, NoHeadException, GitAPIException {
		String userHomeDir = System.getProperty("user.home");
		String gitRepoPath = userHomeDir + PROJECT_GIT_PATH;
		
		File gitWorkDir = new File(gitRepoPath);
		Git git = Git.open(gitWorkDir);
		Iterable<RevCommit> commits = git.log().all().call();
		for (RevCommit revCommit : commits) {
			String commitId = revCommit.getName();
			allCommitMap.put(commitId, revCommit);
		}
	}
	
	private static void writeBugBaseInfo(BufferedWriter xmlWriter, Element bugElement) throws IOException {
		String bugStartTag = "  <bug id=";
		String bugOpenDateAttr = "opendate=";
		String bugFixedDateAttr = "fixdate=";
		
		String bugID = bugElement.getAttribute("id");
		String openDate = bugElement.getAttribute("opendate");
		String fixDate = bugElement.getAttribute("fixdate");
		
		xmlWriter.write(bugStartTag + "\"" + bugID + "\" ");
		xmlWriter.write(bugOpenDateAttr + "\"" + openDate + "\" ");
		xmlWriter.write(bugFixedDateAttr + "\"" + fixDate + "\">\n");
	}
	
	private static void writeBugInformationSection(BufferedWriter xmlWriter, Element bugElement) throws IOException {
		String bugInformationStartTag = "    <buginformation>\n";
		String bugInformationEndTag = "    </buginformation>\n";

		String bugSummaryStartTag = "      <summary>";
		String bugSummaryEndTag = "</summary>\n";
		
		String bugDescriptionStartTag = "      <description>";
		String bugDescriptionEndTag = "</description>\n";

		String commentsStartTag = "      <comments>\n";
		String commentsEndTag = "      </comments>\n";
		
		String commentStartTag = "      <comment id=";
		String commentEndTag = "</comment>\n";
		String commentDateAttr = "date=";
		String commentAuthorAttr = "author=";
		
		xmlWriter.write(bugInformationStartTag);

		xmlWriter.write(bugSummaryStartTag);
		String bugSummary = bugElement.getElementsByTagName("summary").item(0).getTextContent();
		bugSummary = bugSummary.replaceAll("&", "&amp;");
		bugSummary = bugSummary.replaceAll("<", "&lt;");
		bugSummary = bugSummary.replaceAll(">", "&gt;");
		xmlWriter.write(bugSummary);
		xmlWriter.write(bugSummaryEndTag);

		xmlWriter.write(bugDescriptionStartTag);
		String bugDescription = bugElement.getElementsByTagName("description").item(0).getTextContent();
		bugDescription = bugDescription.replaceAll("&", "&amp;");
		bugDescription = bugDescription.replaceAll("<", "&lt;");
		bugDescription = bugDescription.replaceAll(">", "&gt;");

		xmlWriter.write(bugDescription);
		xmlWriter.write(bugDescriptionEndTag);

		System.out.println("Bug ID : " + bugElement.getAttribute("id") + " Open date : "
				+ bugElement.getAttribute("opendate") + " Fixed date : " + bugElement.getAttribute("fixdate"));
//		System.out.println("Summary : " + bugElement.getElementsByTagName("summary").item(0).getTextContent());
//		System.out.println("Description : " + bugElement.getElementsByTagName("description").item(0).getTextContent());

		xmlWriter.write(commentsStartTag);
		
		NodeList comment = bugElement.getElementsByTagName("comment");
		int numComment = comment.getLength();
		for (int i = 0; i < numComment; i++) {
			Node commentNode = comment.item(i);
			Element commentIdElement = (Element) commentNode;

//			System.out.println("Comment ID : " + commentIdElement.getAttribute("id") + " Comment Date : "
//					+ commentIdElement.getAttribute("date") + " Comment Author : "
//					+ commentIdElement.getAttribute("author"));
//			System.out.println("Comment Description : " + bugElement.getElementsByTagName("comment").item(i).getTextContent());

			String commentId = commentIdElement.getAttribute("id");
			String commentDate = commentIdElement.getAttribute("date");
			String commentAuthor = commentIdElement.getAttribute("author");
			String commentDescription = bugElement.getElementsByTagName("comment").item(i).getTextContent();

			commentDescription = commentDescription.replaceAll("&", "&amp;");
			commentDescription = commentDescription.replaceAll("<", "&lt;");
			commentDescription = commentDescription.replaceAll(">", "&gt;");
			xmlWriter.write(commentStartTag + "\"" + commentId + "\" " + commentDateAttr + "\"" + commentDate + "\" "
					+ commentAuthorAttr + "\"" + commentAuthor + "\">" + commentDescription + commentEndTag);
		}
		xmlWriter.write(commentsEndTag);
		xmlWriter.write(bugInformationEndTag);
	}

	private static void writeFixedFilesAndMethodsSection(BufferedWriter xmlWriter, ArrayList<String> commitFiles, HashMap<String, ArrayList<Method>> commitMethods) throws IOException {
		String fileStartTag = "          <file name=";
		String fileEndTag = "          </file>\n";
		String methodStartTag = "              <method name=";
		
		for (int i = 0; i < commitFiles.size(); ++i) {
			String fileName = commitFiles.get(i);
			xmlWriter.write(fileStartTag + "\"" + fileName + "\">\n");
			
			ArrayList<Method> fixedMethods = commitMethods.get(fileName);
			
			if (fixedMethods != null && fixedMethods.size() > 0) {
				for (int j = 0; j > fixedMethods.size(); ++j) {
					Method fixedMethod = fixedMethods.get(j);
					xmlWriter.write(methodStartTag + "\""
							+ fixedMethod.getName() + "\" " + "returnType="
							+ "\"" + fixedMethod.getReturnType() + "\" "
							+ "parameters=" + "\"" + fixedMethod.getParams() + "\"/>"
							+ "\n");
				}
			}
			xmlWriter.write(fileEndTag);
		}
	}
	
	private static void extractCommitFilesAndMethods(RevCommit revCommit, String commitID, ArrayList<String> commitFiles, HashMap<String, ArrayList<Method>> commitMethods) throws IOException, GitAPIException {
		String userHomeDir = System.getProperty("user.home");
		String gitRepoPath = userHomeDir + PROJECT_GIT_PATH;
		
		File gitWorkDir = new File(gitRepoPath);
		Git git = Git.open(gitWorkDir);

		ObjectId oldId = git.getRepository().resolve(commitID + "~1^{tree}");
		ObjectId headId = git.getRepository().resolve(commitID + "^{tree}");
		ObjectReader newObjectReader = git.getRepository().newObjectReader();

		CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
		oldTreeIter.reset(newObjectReader, oldId);
		CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
		newTreeIter.reset(newObjectReader, headId);

		List<DiffEntry> diffs = git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DiffFormatter df = new DiffFormatter(out);
		df.setRepository(git.getRepository());
		
		for (DiffEntry diff : diffs) {
			df.format(diff);
			String diffText = out.toString("UTF-8");
			int chunkHeaderIndex = diffText.indexOf("@@");
			// in case of deleted file
			if (chunkHeaderIndex == -1) {
				continue;
			}

			RevTree tree = revCommit.getTree();
			String oldPath = diff.getOldPath();
			String newPath = diff.getNewPath();
			TreeWalk treeWalk = TreeWalk.forPath(newObjectReader, newPath, tree);
			
			if (newPath.endsWith(".java")) {
				System.out.println("FileName : " + newPath);
				if (newPath != null) {
					String methodData = diffText.substring(chunkHeaderIndex);
					BufferedReader chunk = new BufferedReader(new StringReader(methodData));
					String line = null;
					int chunkStartLineNum = 0;
					int chunkLineCount = 0;

					if ((line = chunk.readLine()) != null) {
						String[] splitWords = line.split(" ")[2].split("[+,]");
						if (splitWords.length >= 3) {
							chunkStartLineNum = Integer.parseInt(splitWords[1]);
							chunkLineCount = Integer.parseInt(splitWords[2]);
							// System.out.println("chunkStart
							// Linenum : " +
							// chunkStartLineNum + " " +
							// chunkLineCount);
						} else if (splitWords.length == 2) { // "--0,0
																// +1"
							chunkStartLineNum = Integer.parseInt(splitWords[1]);
							chunkLineCount = Integer.parseInt(splitWords[1]);
							// System.out.println("chunkStart
							// Linenum : " +
							// chunkStartLineNum + " " +
							// chunkLineCount);
						} else {
							System.exit(-1);
						}
					} else {
						System.exit(-1);
					}

					// add commit file
					commitFiles.add(newPath);
					
					ArrayList<Method> commitMethodList = commitMethods.get(newPath);
					if (null == commitMethodList) commitMethodList = new ArrayList<Method>();
					
					if (treeWalk != null) {
						// use the blob id to read the file's data
						byte[] data = newObjectReader.open(treeWalk.getObjectId(0)).getBytes();
						CompilationUnit cu = getCompilationUnit(new String(data));
						MethodVisitor visitor = new MethodVisitor();
						cu.accept(visitor);
						for (MethodDeclaration md : visitor.methods) {
							for (int startLine = cu.getLineNumber(md.getStartPosition());
									startLine < cu.getLineNumber(md.getStartPosition() + md.getLength());
									startLine++) {
								if (startLine == chunkStartLineNum) {
									System.out.println("Method: " + md.getName());
									System.out.println(
											"Return Type: " + md.getReturnType2());
									System.out.println("Parameter: " + md.parameters());

									String parameters = "";
									for (int l = 0; l < md.parameters().size(); l++) {
										parameters += ((SingleVariableDeclaration) md
												.parameters().get(l)).getType()
												.toString();
										parameters += " ";
									}
									parameters = parameters.trim();

									Method foundMethod = new Method(md.getName().toString(), md.getReturnType2().toString(), parameters);
									commitMethodList.add(foundMethod);
									break;
								}
							}
						}
						
						commitMethods.put(newPath, commitMethodList);
					}
				}
			}
		}
	}
	
	public static void main(String[] args) throws Exception, IOException, NoHeadException, GitAPIException, InterruptedException {
		loadFixedCommits();
		loadAllCommits();

		String productName = TARGET_PRODUCT_NAME;
		String bugRepoFileName = productName + "BugRepository.xml";

		// No comment on the Bug repository file name (before Bug repository
		// Name : ex : SWTBugrepository.xml)
		File bugRepoXmlFile = new File("./data/" + bugRepoFileName);

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(bugRepoXmlFile);

		doc.getDocumentElement().normalize();

//		System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

		BufferedWriter xmlWriter = new BufferedWriter(new FileWriter("./data/" + "Extended" + bugRepoFileName));

		String xmlHeader = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n\n";

		String bugrepositoryStartTag = "<bugrepository name=\"" + productName + "\">\n";
		String bugRepositoryEndTag = "</bugrepository>\n";

		String bugEndTag = "  </bug>\n";

		String fixedCommitsStartTag = "    <fixedCommits>\n";
		String fixedCommitsEndTag = "    </fixedCommits>\n";
		
		String commitStartTag = "      <commit id=";
		String commitEndTag = "      </commit>\n";
		
		xmlWriter.write(xmlHeader);
		xmlWriter.write(bugrepositoryStartTag);

		NodeList bugNodeList = doc.getElementsByTagName("bug");
		for (int j = 0; j < bugNodeList.getLength(); j++) {
			Node bugNode = bugNodeList.item(j);
			if (bugNode.getNodeType() == Node.ELEMENT_NODE) {
				Element bugElement = (Element) bugNode;
				String bugID = bugElement.getAttribute("id");
				
				writeBugBaseInfo(xmlWriter, bugElement);
				writeBugInformationSection(xmlWriter, bugElement);
				
				xmlWriter.write(fixedCommitsStartTag);
				
				HashSet<String> fixedCommitSet = fixedCommitMap.get(bugID);
				Iterator<String> fixedCommitIter = fixedCommitSet.iterator();
				while (fixedCommitIter.hasNext()) {
					String fixedCommitID = fixedCommitIter.next();
					
					RevCommit revCommit = allCommitMap.get(fixedCommitID);
					Date date = revCommit.getAuthorIdent().getWhen();
					String dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(date);
					System.out.println("Commit ID : " + fixedCommitID);
					System.out.println("Author : " + revCommit.getAuthorIdent().getName());
					System.out.println("Date : " + dateFormat);
					
					xmlWriter.write(commitStartTag + "\"" + fixedCommitID + "\" " + "author=" + "\""
							+ revCommit.getAuthorIdent().getName() + "\" " + "date=" + "\"" + dateFormat
							+ "\"/>" + "\n");
					
					ArrayList<String> commitFiles = new ArrayList<String>();
					HashMap<String, ArrayList<Method>> commitMethods = new HashMap<String, ArrayList<Method>>();
					extractCommitFilesAndMethods(revCommit, fixedCommitID, commitFiles, commitMethods);
					writeFixedFilesAndMethodsSection(xmlWriter, commitFiles, commitMethods);
					
					xmlWriter.write(commitEndTag);
				}
				
				xmlWriter.write(fixedCommitsEndTag);
				xmlWriter.write(bugEndTag);
				
				System.out.println("----------------------------");
			}
		}
		xmlWriter.write(bugRepositoryEndTag);
		xmlWriter.close();
	}
}