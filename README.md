# meta-data-action
Script to create datanode list, reference list and pathway info files from one GPML file

Java 11 needed

The MetaDataExtractor.java class requires 5 arguments  
arg1 = repo  
arg2 = file  
arg3 = commitDate (change in GPML)  
arg4 = name of gdb config file  
arg5 = name of organism  
  
The gdb.config file is of the form:
```
Organism name	Organism_Derby_File.bridge  
*	Metabolites_Derby_File.bridge  
```

Example gdb.config file:  
```
Mus musculus	Mm_Derby_Ensembl_103.bridge  
*	metabolites_20210109.bridge 
```

### Using the MetaDataExtractor locally:

Download the latest jar file from: https://github.com/wikipathways/meta-data-action/releases/download/1.0.0/meta-data-action-1.0.3-jar-with-dependencies.jar

In your command line, navigate to the folder where you saved the jar file. 

Then run the following command, providing the 5 arguments described above: 

```bash
mkdir -o pathways/WP1
java -jar meta-data-action-1.0.3-jar-with-dependencies.jar wikipathways/wikipathways-database pathways/WP1/WP1.gpml 2022-10-04 gdb.config "Mus musculus"
```

The above command uses as an example WP1, an example date (2022-10-04), and an example organism name (Mus musculus). 

The gdb.config file needs to be saved in the same directory as the meta-data-action jar file.

*Advanced: If you want to run with local GPML files, then set the repo arg to "local". Adapt and execute `scripts/local-run/on-gpml-change_local.sh` for bulk, offline use cases.*

### Using the MetaDataExtractor through a GitHub Action: 
  
The MetaDataExtractor main class is called by on_gpml_change.yml (https://github.com/wikipathways/wikipathways-database/blob/main/.github/workflows/on_gpml_change.yml)  
  
Before calling the MetaDataExtractor.java, two shell scripts are called: configGenerator.sh and installDependencies.sh  
  
configGenerator.sh:  
First, generates fileNames.config and fileDownloads.config  
These files are generated using gene.json and other.json, available from the BridgeDb GitHub repository:  
gene.json: https://bridgedb.github.io/data/gene.json  
other.json: https://bridgedb.github.io/data/other.json  
  
fileNames.config: the organism's name and its derby database file name  
fileDownloads.config: the derby database and its download URL  
  
From these two config files, configGenerator.sh is able to generate the gdb.config file with the correct derby database name and download URL.  
  
installDependencies.sh:  
Ensures that the required derby databases are downloaded.  
First checks if the files already exist in the cache, then downloads required files.  
  
Once configGenerator.sh and installDependencies.sh are run, the MetaDataExtractor is able to generate info.json and datanodes.tsv  
  
Todo:  
- [x] Fix pom.xml file to build jar file (with integrated dependencies)
