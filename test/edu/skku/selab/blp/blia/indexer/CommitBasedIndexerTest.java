package edu.skku.selab.blp.blia.indexer;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import edu.skku.selab.blp.Property;
import edu.skku.selab.blp.common.Bug;

public class CommitBasedIndexerTest {
	
	static int verID = 0;
	private static Git git;
	private static Repository repo;
	private static RevWalk revWalk;
	private static LogCommand log;
	
	static public void main(String[] args) throws Exception{
		Property.loadInstance();
		String commitSrc = Property.getInstance().getRepoDir();
		System.out.println(commitSrc);
		git = Git.open(new File(commitSrc));
		repo = git.getRepository();
		git.checkout().setName("master").call();
		
		Iterator<RevCommit> commitLogs = git.log().call().iterator();
		ArrayList<RevCommit> commitList = new ArrayList<RevCommit>();
		ArrayList<Bug> bugList = BugCorpusCreator.parseXML(Property.getInstance().isStraceScoreIncluded());
		bugList = sort(bugList);
		
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date firstFixedDate = dateFormat.parse(bugList.get(0).getFixedDateString());

		String prevCommit = "";
		boolean firstFlag = false;
		while (commitLogs.hasNext()) {
			RevCommit currentCommit = commitLogs.next();
			commitList.add(currentCommit);
			long timestamp = (long) currentCommit.getCommitTime() * 1000;
			Date commitDate = new Date(timestamp);			
			if(commitDate.before(firstFixedDate)){
				prevCommit = currentCommit.toString().split(" ")[1];
				break;
			}
			
		}
		System.out.println(prevCommit);
		
		git.checkout().setName(prevCommit).call();
		
		HashMap<Integer, ArrayList<String>> commitedFileList = new HashMap<Integer, ArrayList<String>>(); 
		for(int i = 1; i<bugList.size(); i++){
			ArrayList<String> fileList = new ArrayList<String>();
			Date fixedDate = dateFormat.parse(bugList.get(i).getFixedDateString());
			String fixedPrevCommit = "";
			for(int j = 0 ; j<commitList.size(); j++){
				RevCommit currentCommit = commitList.get(j);
				commitList.add(currentCommit);
				long timestamp = (long) currentCommit.getCommitTime() * 1000;
				Date commitDate = new Date(timestamp);		
				if(commitDate.before(fixedDate)){
					fixedPrevCommit = currentCommit.toString().split(" ")[1];
					break;		
				}
			}

			git.checkout().setName(fixedPrevCommit).call();
			

			ObjectReader reader = repo.newObjectReader();
			ObjectId prevCommitObject = repo.resolve(prevCommit+"^{tree}");
			ObjectId prevFixedCommitObject = repo.resolve(fixedPrevCommit+"^{tree}");
			CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
    		oldTreeIter.reset(reader, prevCommitObject);
    		CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
    		newTreeIter.reset(reader, prevFixedCommitObject);

    		// finally get the list of changed files
    		try (Git git = new Git(repo)) {
                List<DiffEntry> diffs= git.diff()
        		                    .setNewTree(newTreeIter)
        		                    .setOldTree(oldTreeIter)
        		                    .call();
                int num = 0;
                for (DiffEntry entry : diffs) {
                	if(entry.toString().contains(".java]")){
//	                    System.out.println(num+" Entry: " + entry);
	                    fileList.add(entry.toString().replace("Entry: DiffEntry[", "").replace("]",""));
	                    num++;
                	}
                }
    		}
    		commitedFileList.put(bugList.get(i).getID(), fileList);
			
			prevCommit = fixedPrevCommit;
			verID++;		
		}
		System.out.println(commitedFileList);
	}
	
	private static ArrayList<Bug> sort(ArrayList<Bug> list) {
		HashMap<String, String> dateWithMap = new HashMap<String, String>();
		ArrayList<Bug> result = new ArrayList<Bug>();
		
		for(int i = 0 ; i<list.size(); i++){
			dateWithMap.put(String.valueOf(list.get(i).getID()), list.get(i).getFixedDateString());
		}
		
		Map.Entry<String, String>[] entries = dateWithMap.entrySet().toArray(new Map.Entry[dateWithMap.size()]);
		Arrays.sort(entries, new Comparator<Map.Entry<String, String>>() {
		    SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd");

		    @Override
		    public int compare(Map.Entry<String, String> o1, Map.Entry<String, String> o2) {
		        try {
		            return date.parse(o1.getValue()).compareTo(date.parse(o2.getValue()));
		        } catch (ParseException e) {
		            throw new AssertionError(e);
		        }
		    }
		});
		for (Map.Entry entry : entries) {			
			for(int i = 0 ; i<list.size(); i++){
				if(String.valueOf(entry.getKey()).equals(String.valueOf(list.get(i).getID())))
					result.add(list.get(i));
			}		    
		}
		
		// TODO Auto-generated method stub
		return result;
	}

}
