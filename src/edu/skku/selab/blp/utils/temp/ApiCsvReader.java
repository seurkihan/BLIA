package edu.skku.selab.blp.utils.temp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.Statement;

import edu.skku.selab.blp.db.dao.BaseDAO;

public class ApiCsvReader {
	
	static String path = "./data/api_info(FSE)";
	static String project = "swt";
	
	
	public static void apiRead(String project, Connection conn) throws Exception{
		Statement q = conn.createStatement();
		BufferedReader br = new BufferedReader(new FileReader(path+"\\"+project+".csv"));
		
		q.execute("DELETE FROM API_DESC_INFO");
		
		String str;
		while((str= br.readLine())!= null){
			
			try{
				Integer.parseInt(str.split(",")[0]);
			}catch(Exception e){
				break;
			}
//			System.out.println(str);
			String fileName = str.split(",")[1].replaceAll("'", ".");
			String desc = "";
			if(str.split(",").length > 2)
				desc = str.split(",")[2].replaceAll("'", ".").replaceAll("\"", ".");
			String importName = fileName.split("/")[fileName.split("/").length-1].replace(".html", "");
			
			
			q.execute("INSERT INTO API_DESC_INFO  (FILE_NAME,DESC,IMPORT_NAME) VALUES ('"+fileName+"','"+desc+"','"+importName+"');");
			
		}
		
		conn.close();
		
	}
	
	
	
	public static void main(String[] args) throws Exception{
		Connection conn = openConnetion(project);
		Statement q = conn.createStatement();
		BufferedReader br = new BufferedReader(new FileReader(path+"\\"+project+".csv"));
		
		q.execute("DELETE FROM API_DESC_INFO");
		
		String str;
		while((str= br.readLine())!= null){
			
			try{
				Integer.parseInt(str.split(",")[0]);
			}catch(Exception e){
				break;
			}
			System.out.println(str);
			String fileName = str.split(",")[1].replaceAll("'", ".");
			String desc = "";
			if(str.split(",").length > 2)
				desc = str.split(",")[2].replaceAll("'", ".").replaceAll("\"", ".");
			String importName = fileName.split("/")[fileName.split("/").length-1].replace(".html", "");
			
			
			q.execute("INSERT INTO API_DESC_INFO  (FILE_NAME,DESC,IMPORT_NAME) VALUES ('"+fileName+"','"+desc+"','"+importName+"');");
			
		}
		
		conn.close();
		
	}
	
	public static Connection openConnetion(String dbName) throws Exception{
		BaseDAO.openConnection(dbName);
		return BaseDAO.getAnalysisDbConnection();
	}

}
