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
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.pathvisio.core.biopax.PublicationXref;
import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.OntologyTag;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.model.PathwayElement.Comment;

/**
 * 
 * Class to extract meta data from GPML file and commit meta data 
 * files to WikiPathways database repo
 * @author mkutmon
 *
 */
public class MetaDataExtractor {

	private static String repo;
	private static String file;
	private static String date;
	private static File folder;

	/**
	 * 
	 * @param args
	 * arg1 = repo
	 * arg2 = file
	 * arg3 = commitDate (change in GPML)
	 */
	public static void main(String[] args) {
		if(args.length == 3) {
			File localDir = new File("pathways");
			repo = args[0];
			file = args[1];
			date = args[2];
			
			try {
				if(file.endsWith(".gpml")) {
					URL url = new URL("https://raw.githubusercontent.com/" + repo + "/main/" + file);
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
					 
					Pathway p = new Pathway();
					p.readFromXml(url.openStream(), false);
					String rev = p.getMappInfo().getVersion().split("_")[1];
					printPathwayInfo(id, rev, p.getMappInfo().getAuthor(), date, p);
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
	
	private static void printPathwayInfo(String pId, String revision, String authors, String date, Pathway pwy) throws IOException {
		System.out.println("print pathway info");
		JSONObject jsonObject = new JSONObject();

		List<String> a = new ArrayList<String>();
		authors = authors.replace("[", "").replace("]", "");
		String[] list = authors.split(", ");
		List<String> aList = new ArrayList<>();
		for (String s : list) {
			aList.add(s);
		}
		jsonObject.put("authors", aList);

		String desc = "";
		for (Comment c : pwy.getMappInfo().getComments()) {
			if (c.getSource() != null) {
				if (c.getSource().equals("WikiPathways-description")) {
					desc = c.getComment();
				}
			}
		}

		jsonObject.put("description", desc.replace("\n", " "));

		System.out.println(date);
		jsonObject.put("last-edited", date.substring(0, 10));

		List<String> ont = new ArrayList<>();
		for (OntologyTag t : pwy.getOntologyTags()) {
			ont.add(t.getId());
		}
		jsonObject.put("ontology-ids", ont);

		List<String> org = new ArrayList<>();
		org.add(pwy.getMappInfo().getOrganism());
		jsonObject.put("organisms", org);

		jsonObject.put("revision", revision);

		jsonObject.put("title", pwy.getMappInfo().getMapInfoName());

		jsonObject.put("wpid", pId);

		File file = new File(folder, pId + "-info.json");
		FileWriter writer = new FileWriter(file.getAbsoluteFile());
		writer.write(jsonObject.toString(4));
		writer.close();

	}
	
	private static void printNodeList(String pId, Pathway pwy) throws IOException {
		File file = new File(folder, pId + "-datanodes.tsv");
		BufferedWriter w = new BufferedWriter(new FileWriter(file));
		w.write("Label\tType\tID\tDatabase\tComment\n");
		for(PathwayElement e : pwy.getDataObjects()) {
			if(e.getObjectType().equals(ObjectType.DATANODE)) {
				String comment = "";
				for(Comment c : e.getComments()) {
					comment = comment + c.getComment().replace("\n", " ") + "</br>"; 
				}
				if(!comment.equals("")) comment = comment.substring(0, comment.length()-5);
				w.write(e.getTextLabel().replace("\n", "") + "\t" + e.getDataNodeType() + "\t" + ((e.getElementID() != null) ? e.getElementID() : "") + "\t" + ((e.getDataSource() != null) ? e.getDataSource().getFullName() : "") + "\t" +  comment + "\n");			
			}
		}
		w.close();
	}
	
	private static void printRefList(String pId, Pathway pwy) throws IOException {
		File file = new File(folder, pId + "-refs.tsv");
		BufferedWriter w = new BufferedWriter(new FileWriter(file));
		w.write("ID\tDatabase\n");
		Set<String> refs = new HashSet<String>();

		for(PathwayElement e : pwy.getDataObjects()) {
			if(!e.getObjectType().equals(ObjectType.BIOPAX)) {
				for(PublicationXref px : e.getBiopaxReferenceManager().getPublicationXRefs()) {
					if(!refs.contains(px.getPubmedId())) {
						w.write(px.getPubmedId() + "\tPubmed\n");
						refs.add(px.getPubmedId());
					}
				}
			}
		} 
		w.close();
	}
}
