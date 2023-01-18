//Copyright 2022 WikiPathways
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package meta.data.action;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bridgedb.DataSource;
import org.bridgedb.IDMapperException;
import org.bridgedb.IDMapperStack;
import org.bridgedb.Xref;
import org.bridgedb.bio.DataSourceTxt;
import org.bridgedb.bio.Organism;
import org.bridgedb.rdb.GdbProvider;
import org.json.JSONObject;
import org.pathvisio.libgpml.io.ConverterException;
import org.pathvisio.libgpml.model.Annotation;
import org.pathvisio.libgpml.model.Citation;
import org.pathvisio.libgpml.model.DataNode;
import org.pathvisio.libgpml.model.Pathway.Author;
import org.pathvisio.libgpml.model.PathwayElement.AnnotationRef;
import org.pathvisio.libgpml.model.PathwayElement.Comment;
import org.pathvisio.libgpml.model.PathwayModel;

/**
 * 
 * Class to extract meta data from GPML file and commit meta data 
 * files to WikiPathways database repo
 * @author mkutmon, hbasaric
 *
 */
public class MetaDataExtractor {

	private static String repo;
	private static String file;
	private static String date;
	private static String gdbFileName;
	private static String organismName;
	private static File folder;

	/**
	 * 
	 * @param args
	 * arg1 = repo
	 * arg2 = file
	 * arg3 = commitDate (change in GPML)
	 * arg4 = name of gdb config file
	 * arg5 = name of organism
	 * @throws ClassNotFoundException 
	 * @throws IDMapperException 
	 */
	public static void main(String[] args) throws ClassNotFoundException, IDMapperException {
		if(args.length == 5) {
			File localDir = new File("pathways");
			repo = args[0];
			file = args[1];
			date = args[2];
			gdbFileName = args[3];
			organismName = args[4];
			
			try {
				if(file.endsWith(".gpml")) {
					//extract WPID and verify folder path
					String [] buffer = file.split("/");
					String id = "";
					for(String s : buffer) {
						if(!s.endsWith(".gpml")) {
							File f = new File(localDir, s);
							folder = f;
						} else {
							id = s.replace(".gpml", "");
						}
					}
					System.out.println(folder.getAbsolutePath() + "\t" + folder.exists());
					PathwayModel p = new PathwayModel();
					//Using local GPML files (file = relative file path)
					if(repo.equalsIgnoreCase("local")){
						InputStream fileIS = new FileInputStream(new File(file));
						p.readFromXml(fileIS, false);
					} else {
					//Using repo GPML files
						URL url = new URL("https://raw.githubusercontent.com/" + repo + "/main/" + file);
						p.readFromXml(url.openStream(), false);
					}
					String rev = p.getPathway().getVersion().split("_")[1];
					String[] auth = p.getPathway().getDynamicProperties().get("pathway_author_gpml2013a").replaceAll("^\\s*\\[|\\]\\s*$", "").split("\\s*,\\s*");
					
					for (String a : auth) {
						p.getPathway().addAuthor(a);
					}
					printPathwayInfo(id, rev, p.getPathway().getAuthors(), date, p);
					printNodeList(id, p);
					printRefList(id, p);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ConverterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			System.out.println("Wrong number of attributes");
		}
	}
	
	private static void printPathwayInfo(String pId, String revision, List<Author> authors, String date, PathwayModel p) throws IOException {
		System.out.println("print pathway info");
		JSONObject jsonObject = new JSONObject();
		List<String> a = new ArrayList<String>();
		
		for(Author auth : authors) {
			a.add(auth.getName());
		}
		
		jsonObject.put("authors", a);

		String desc = "";
		desc = p.getPathway().getDescription();
		if (null == desc){
			desc = "";
		}

		jsonObject.put("description", desc.replace("\n", " "));

		jsonObject.put("last-edited", date.substring(0, 10));

		List<String> ont = new ArrayList<>();
		
		for (AnnotationRef ann : p.getPathway().getAnnotationRefs()) {
			ont.add(ann.getAnnotation().getXref().getBioregistryIdentifier().toUpperCase());
		}
		jsonObject.put("ontology-ids", ont);

		List<String> org = new ArrayList<>();
		org.add(p.getPathway().getOrganism());
		jsonObject.put("organisms", org);

		jsonObject.put("revision", revision);

		jsonObject.put("title", p.getPathway().getTitle());

		jsonObject.put("wpid", pId);

		File file = new File(folder, pId + "-info.json");
		FileWriter writer = new FileWriter(file.getAbsoluteFile());
		writer.write(jsonObject.toString(4));
		writer.close();

	}
	
	private static void printNodeList(String pId, PathwayModel p) throws IOException, ClassNotFoundException, IDMapperException {
		File file = new File(folder, pId + "-datanodes.tsv");
		BufferedWriter w = new BufferedWriter(new FileWriter(file));
		w.write("Label\tType\tIdentifier\tComment\tEnsembl\tNCBI gene\tHGNC\tUniProt\tWikidata\tChEBI\tInChI\n");
		ArrayList<String> elementTypes = new ArrayList<String>(Arrays.asList("Metabolite", "GeneProduct", "Protein"));
		
		// create idmapper stack using gdb.config file
		GdbProvider idmp = new GdbProvider();
		File gdbFile = new File(gdbFileName);
		idmp = GdbProvider.fromConfigFile(gdbFile);
		Organism org = Organism.fromLatinName(organismName);
		IDMapperStack idmpStack = idmp.getStack(org);
		
		for (String type : elementTypes){
			for(DataNode e : p.getDataNodes()) {
				if(e.getType().toString().equals(type)){
					String comment = "";
					for(Comment c : e.getComments()) {
						comment = comment + c.getCommentText().replace("\n", " ") + "</br>"; 
					}
					comment = comment.replace("\"", "\'"); //for jekyll TSV parsing
					String idMappings = "";	//idMappings string which will be used in the final datanodes.tsv table
					String sourceDb = "";
					if(null != e.getXref()){
						sourceDb = e.getXref().toString().replaceAll("[\\[\\](){}]","");
						// call helper function which takes the original identifier (e.getElementID() and performs identifier mappings
						// it will append the list of IDs to the idMappings string and return the string with the filled out blanks for each database (some will remain blank)
						String bioregID = null;
						if (null != e.getXref().getBioregistryIdentifier())
							bioregID = e.getXref().getBioregistryIdentifier().replaceAll("chebi:CHEBI:", "chebi:");
						idMappings = getIDMappingsString(e, pId, p, idmpStack);
						if(!comment.equals("")) comment = comment.substring(0, comment.length()-5);
						w.write(e.getTextLabel().replace("\n", "") + "\t" + e.getType() + "\t" + ((bioregID != null) ? bioregID : "") + "\t" +  comment  + "\t" + idMappings + "\n");			
					}
				}
			}
		}
		w.close();
	}
	
	private static String getIDMappingsString(DataNode e, String pId, PathwayModel p, IDMapperStack idmpStack) throws ClassNotFoundException, IOException, IDMapperException {
		// perform ID Mapping for Ensembl, NCBI gene, HGNC, UniProt, Wikidata, ChEBI, InChI
		//DataSourceTxt.init();
		ArrayList<String> dataSourceList = new ArrayList<String>(Arrays.asList("En", "L", "H", "S", "Wd", "Ce","Ik"));
		String result = "";		
		
		// For each data source in the header, mapID and append the result to the string.
		for (String sysCode : dataSourceList) {
			Set<Xref> stackResult = idmpStack.mapID(e.getXref(), DataSource.getExistingBySystemCode(sysCode));
			// Check if more than one Xref was returned. If yes, then append them using semicolon.
			String stackStr = "";
			for (Xref ref : stackResult) {
				String bioregID = ref.getBioregistryIdentifier();
				bioregID = bioregID.replaceAll("chebi:CHEBI:", "chebi:");
				if (stackResult.size() > 1) {
					stackStr = stackStr + bioregID + ";";
				}
				else {
					stackStr = bioregID;
				}
			}
			if (stackStr.endsWith(";")){
				stackStr = stackStr.substring(0, stackStr.length()-1);
			}
			if (sysCode == "En") {
				result = stackStr;
			}
			else {
				result = result+ "\t" + stackStr;
			}
		}
		return result;
	}

	private static void printRefList(String pId, PathwayModel p) throws IOException {
		File file = new File(folder, pId + "-refs.tsv");
		BufferedWriter w = new BufferedWriter(new FileWriter(file));
		w.write("ID\tDatabase\n");
		Set<Xref> refs = new HashSet<Xref>();

		for(Citation e : p.getCitations()) {
			if(!refs.contains(e.getXref())) {
				w.write(e.getXref().getId()+ "\t" + e.getXref().getDataSource().getFullName() + "\n");
				refs.add(e.getXref());
			}
		} 
		w.close();
	}
}
