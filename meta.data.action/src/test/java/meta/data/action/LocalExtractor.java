package meta.data.action;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.OntologyTag;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.model.PathwayElement.Comment;

public class LocalExtractor {

	// for local testing
	public static void main(String[] args) throws Exception {
		File dir = new File("D:\\Github\\wikipathways-database\\pathways");
		for (File subdir : dir.listFiles()) {
			System.out.println(subdir);
			String date = "";
			File info = new File(subdir, subdir.getName() + ".info");
			BufferedReader reader = new BufferedReader(new FileReader(info));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains("last-edited")) {
					date = line.replace("last-edited: ", "");
				}
			}
			File gpml = new File(subdir, subdir.getName() + ".gpml");
			if (gpml.getName().endsWith(".gpml")) {
				Pathway p = new Pathway();
				p.readFromXml(gpml, false);
				String rev = p.getMappInfo().getVersion().split("_")[1];
				String id = gpml.getName().replace(".gpml", "");
				printPathwayInfo(subdir, id, rev, p.getMappInfo().getAuthor(), date, p);
				printNodeList(subdir, id, p);
			}

		}
	}

	private static void printPathwayInfo(File folder, String pId, String revision, String authors, String date,
			Pathway pwy) throws IOException {
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

		jsonObject.put("last-edited", date.substring(0, 8));

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
	
	private static void printNodeList(File folder, String pId, Pathway pwy) throws IOException {
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

}
