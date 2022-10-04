package meta.data.action;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.pathvisio.libgpml.model.Pathway.Author;
import org.pathvisio.libgpml.model.PathwayElement.AnnotationRef;
import org.pathvisio.libgpml.model.DataNode;
import org.pathvisio.libgpml.model.PathwayModel;
import org.pathvisio.libgpml.model.PathwayElement.Comment;


public class LocalExtractor {
	private static String repo;
	private static String file;
	private static String date;
	private static String gdbFileName;
	private static String organismName;
	private static File folder;
	
	// for local testing
	public static void main(String[] args) throws Exception {
		File dir = new File("C:\\Users\\Helena\\git\\wikipathways-database\\pathwaysTest");
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
				PathwayModel p = new PathwayModel();
				p.readFromXml(gpml, false);
				String rev = p.getPathway().getVersion().split("_")[1];
				String id = gpml.getName().replace(".gpml", "");
				System.out.println("printing pathway info");
				printPathwayInfo(subdir, id, rev, p.getPathway().getAuthors(), date, p);
				printNodeList(subdir, id, p);
			}

		}
	}

	private static void printPathwayInfo(File folder, String pId, String revision, List<Author> list2, String date,
			PathwayModel pwy) throws IOException {
		JSONObject jsonObject = new JSONObject();

		List<String> a = new ArrayList<String>();
		for(Author auth : list2) {
			a.add(auth.getUsername());
		}
		
		jsonObject.put("authors", a);

		String desc = "";
		desc = pwy.getPathway().getDescription();

		jsonObject.put("description", desc.replace("\n", " "));

		jsonObject.put("last-edited", date.substring(0, 8));

		List<String> ont = new ArrayList<>();
		for (AnnotationRef ann : pwy.getPathway().getAnnotationRefs()) {
			//ann.getAnnotation().getXref();
			ont.add(ann.getAnnotation().getXref().getId());
		}
		jsonObject.put("ontology-ids", ont);

		List<String> org = new ArrayList<>();
		org.add(pwy.getPathway().getOrganism());
		jsonObject.put("organisms", org);

		jsonObject.put("revision", revision);

		jsonObject.put("title", pwy.getPathway().getTitle());

		jsonObject.put("wpid", pId);

		File file = new File(folder, pId + "-info.json");
		FileWriter writer = new FileWriter(file.getAbsoluteFile());
		writer.write(jsonObject.toString(4));
		writer.close();

	}
	
	private static void printNodeList(File folder, String pId, PathwayModel pwy) throws IOException, ClassNotFoundException, IDMapperException {
		File file = new File(folder, pId + "-datanodes.tsv");
		BufferedWriter w = new BufferedWriter(new FileWriter(file));
		w.write("Label\tType\tIdentifier\tDatabase\tComment\tEnsembl\tNCBI gene\tHGNC\tUniProt\tWikidata\tChEBI\tInChI\n");
		ArrayList<String> elementTypes = new ArrayList<String>(Arrays.asList("Metabolite", "GeneProduct", "Protein"));
		
		// create idmapper stack using gdb.config file
		GdbProvider idmp = new GdbProvider();
		gdbFileName = "C:\\Users\\Helena\\Documents\\Projects\\BridgeDb\\config_files\\gdbTestMm.config";
		
		File gdbFile = new File(gdbFileName);
		idmp = GdbProvider.fromConfigFile(gdbFile);
		Organism org = Organism.fromLatinName(organismName);
		IDMapperStack idmpStack = idmp.getStack(org);
		for (String type : elementTypes){
			for(DataNode e : pwy.getDataNodes()) {
				if(e.getType().toString().equals(type)){
					String comment = "";
					for(Comment c : e.getComments()) {
						comment = comment + c.getCommentText().replace("\n", " ") + "</br>"; 
					}
					comment = comment.replace("\"", "\'"); //for jekyll TSV parsing
					String idMappings = "";	//idMappings string which will be used in the final datanodes.tsv table
					String sourceDb = "";
					sourceDb = e.getXref().toString().replaceAll("[\\[\\](){}]","");
					// call helper function which takes the original identifier (e.getElementID() and performs identifier mappings
					// it will append the list of IDs to the idMappings string and return the string with the filled out blanks for each database (some will remain blank)
					String bioregID = e.getXref().getBioregistryIdentifier().replaceAll("chebi:CHEBI:", "chebi:");
					idMappings = getIDMappingsString(e, pId, pwy, idmpStack);
					if(!comment.equals("")) comment = comment.substring(0, comment.length()-5);
					w.write(e.getTextLabel().replace("\n", "") + "\t" + e.getType() + "\t" + ((bioregID != null) ? bioregID : "") + "\t" + ((e.getXref().getDataSource() != null) ? sourceDb : "") + "\t" +  comment  + "\t" + idMappings + "\n");			
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
}
