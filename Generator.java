import Exceptions.NoReverseRelationException;
import Exceptions.RelatedTableNotFoundException;
import Exceptions.SelfRelationException;
import Exceptions.UnknownRelationException;
import com.esotericsoftware.yamlbeans.YamlReader;

import java.io.*;
import java.util.*;

public class Generator {
    private final File in;
    private final File out;
    private Map data;
    private StringBuilder output = new StringBuilder("");
    private StringBuilder relations = new StringBuilder("");
    private Map<String, String> manyToMany = new HashMap();
    private final YamlReader reader;

    private static String updateTrigger = "";


    public Generator(File in, File out) throws IOException {
        String line;
        BufferedReader updateReader = new BufferedReader(new FileReader("src/AllTheText/updateTrigger"));
        while ((line = updateReader.readLine()) != null) {
            Generator.updateTrigger += line;
            Generator.updateTrigger += "\n";
        }

        this.in = in;
        this.out = out;
        this.reader = new YamlReader(new FileReader(this.in));
    }

    public void create() throws IOException {
        this.data = (Map) reader.read();
        Set keys = this.data.keySet();

        for (Iterator i = keys.iterator(); i.hasNext(); ) {
            String tableName = i.next().toString();
            String tableData = this.data.get(tableName).toString();

//            tableName = tableName.toLowerCase();

            this.output.append("CREATE TABLE \"" + tableName + "\" (\n");

            if (tableData.contains(", relations={")) {
                if (tableData.startsWith("{fields={")) { // exptected to be {fields={
                    String namesAndTypes = tableData.substring(9, tableData.lastIndexOf(", relations={") - 1);
                    String relationsData = tableData.substring(tableData.lastIndexOf("relations={") + 11, tableData.length() - 2); // -2 }}
                    this.addFields(tableName, namesAndTypes);
                    this.addRelations(tableName, relationsData);
                }
            } else {
                if (tableData.startsWith("{fields={")) {
                    String namesAndTypes = tableData.substring(9, tableData.lastIndexOf("}") - 1);
                    this.addFields(tableName, namesAndTypes);
                }
            }

            this.output.append("  \"" + tableName + "_created\" INTEGER NOT NULL DEFAULT cast(extract(epoch from now()) AS INTEGER),\n");
            this.output.append("  \"" + tableName + "_updated\" INTEGER NOT NULL DEFAULT cast(extract(epoch from now()) AS INTEGER)\n);\n\n");
            this.output.append(String.format(Generator.updateTrigger, tableName) + "\n");
        }

        if(!this.relations.toString().isEmpty()) {
            this.output.append("\n\n");
            this.output.append(this.relations.toString());
        }

        FileWriter fw = new FileWriter(this.out);
        fw.write(this.output.toString());
        fw.close();
        this.reader.close();

    }

    private void addFields(final String tableName, final String namesAndTypes) {
        String[] temp = namesAndTypes.split(",");

        this.output.append("  \"" + tableName + "_id\" SERIAL PRIMARY KEY,\n");
        for(String nameAndType : temp) {
            this.output.append("  \"" + tableName + "_" + nameAndType.split("=")[0].trim() + "\" " + nameAndType.split("=")[1].trim() + ",\n");
        }
    }

    private void addRelations(final String tableName, final String relationsData) {
        String[] temp = relationsData.split(",");

        for(String relation : temp) { // Category=one, User=many inside current tableName
            try{
                this.checkReverseRelation(tableName, relation);
            } catch(NoReverseRelationException e) {
                e.printStackTrace();
            } catch(SelfRelationException e) {
                e.printStackTrace();
            } catch (RelatedTableNotFoundException e) {
                e.printStackTrace();
            } catch (UnknownRelationException e) {
                e.printStackTrace();
            }
        }
    }

    private void checkReverseRelation(final String tableName, final String relation) throws NoReverseRelationException, SelfRelationException, RelatedTableNotFoundException, UnknownRelationException {
        String relationTableName = relation.split("=")[0].trim();
        String relationType = relation.split("=")[1].trim();

        if(!relationType.equalsIgnoreCase("one") && !relationType.equalsIgnoreCase("many")) {
            throw new UnknownRelationException();
        }
        if(relationTableName.equalsIgnoreCase(tableName)) { // no self relating
            throw new SelfRelationException();
        }

        String tableData;
        try {
            tableData = this.data.get(relationTableName).toString(); // so far case sensitive
        } catch (NullPointerException e) {
            throw new RelatedTableNotFoundException();
        }

        String relationData = "";
        if(tableData.contains("relations={")) {
            relationData = tableData.substring(tableData.lastIndexOf("relations={") + 11, tableData.length() - 2); // all relations (..=.., ..=..)
            if(relationData.contains(tableName + "=")) { // if there is a mention of reverse Article relations:User=one - User relations:Article-many
                                                         // relationData.split(tableName)[1]; to get Category=one to =one
                String reverseRelationType = relationData.split(tableName)[1].substring(0, 4); // either =one or =man
                if(!reverseRelationType.equalsIgnoreCase("=one") && !reverseRelationType.equalsIgnoreCase("=man")) {
                    throw new UnknownRelationException();
                }                                                                                                // Category:
                                                                                                                 //  relations:
                if(reverseRelationType.equalsIgnoreCase("=one") && relationType.equalsIgnoreCase("many")) {      //   Article:one
                    return;
                } else if(reverseRelationType.equalsIgnoreCase("=man") && relationType.equalsIgnoreCase("one")) { // Check
                    this.addOTMRelation(tableName, relationTableName); // alter Article table with fk from Category
                } else { // if many and many
                    if(this.manyToMany.get(tableName) == null && this.manyToMany.get(relationTableName) == null) {
                        this.createMTMTable(tableName, relationTableName);
                        this.addMTMRelation(tableName, relationTableName);
                        this.manyToMany.put(tableName, relationTableName);
                    } else {
                        return;
                    }
                }
            }
        } else {
            throw new NoReverseRelationException();
        }
    }

    //tableName - one
    //reverseTableName - many

    // One To Many
    private void addOTMRelation(final String tableName, final String reverseTableName) { // one - many
        this.relations.append("ALTER TABLE " + "\"" + tableName +"\" " + "ADD \"" + reverseTableName + "_id\" " + "INTEGER NOT NULL,\n");
        this.relations.append("  ADD CONSTRAINT \"fk_" + tableName + "_" + reverseTableName + "_id\" ");
        this.relations.append("FOREIGN KEY (\"" + reverseTableName + "_id\")" + " REFERENCES \"" + reverseTableName + "\" ");
        this.relations.append("(\"" + reverseTableName + "_id\");\n\n");

    }

    // Many To Many
    private void addMTMRelation(final String tableName1, final String tableName2) {
        String manyToManyTableName = tableName1 + "__" + tableName2;
        this.relations.append("ALTER TABLE \"" + manyToManyTableName + "\"\n");
        this.relations.append("  ADD CONSTRAINT \"fk_" + manyToManyTableName + "_" + tableName1 + "_id\"" + " FOREIGN KEY (\"");
        this.relations.append(tableName1 + "_id\") REFERENCES \"" + tableName1 + "\" (\"" + tableName1 + "_id\");\n\n");

        this.relations.append("ALTER TABLE \"" + manyToManyTableName + "\"\n");
        this.relations.append("  ADD CONSTRAINT \"fk_" + manyToManyTableName + "_" + tableName2 + "_id\"" + " FOREIGN KEY (\"");
        this.relations.append(tableName1 + "_id\") REFERENCES \"" + tableName2 + "\" (\"" + tableName2 + "_id\");\n\n");
    }
    private void createMTMTable(final String tableName1, final String tableName2) {
        this.relations.append("CREATE TABLE \"" + tableName1 + "__" + tableName2 + "\" (\n");
        this.relations.append("  \"" + tableName1 + "_id\" INTEGER NOT NULL,\n");
        this.relations.append("  \"" + tableName2 + "_id\" INTEGER NOT NULL,\n");
        this.relations.append("  PRIMARY KEY (\"" + tableName1 + "_id\", " + "\"" + tableName2 + "_id\")\n);\n\n");
    }
}

// doesn't work if:
// -several relations with the same tableName
// -tables in db doesn't start with capital letter
// still works if:
// -relations:TableName:man (man = many but without y)
