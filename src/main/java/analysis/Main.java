package analysis;

import indexation.VisualGenomeIndexer;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.*;
import net.didion.jwnl.data.list.PointerTargetNode;
import net.didion.jwnl.data.list.PointerTargetNodeList;
import net.didion.jwnl.dictionary.Dictionary;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.json.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.lucene.store.Directory;
import prep.Graph;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import static indexation.GraphIndexer.indexGraph;

public class Main {

    private static Directory graphDirectory, VGDirectory;
    private static String logString = "";
    private static String lineSeparator = System.getProperty("line.separator");
    private static Dictionary dictionary;

    private static Hashtable<String, Boolean> resultTab;
    private static Hashtable<String, WordVec> storedVectors;


    public static void main(String[] args) throws Exception {
        /*
        JWNL.initialize(new FileInputStream("src/main/resources/properties.xml"));
        dictionary = Dictionary.getInstance();
        */

        graphDirectory = FSDirectory.open(Paths.get("src", "main", "resources", "index"));

        Graph graph = new Graph("WN_DSR_model_XML.rdf");
        indexGraph(graph, graphDirectory);

        VGDirectory = FSDirectory.open(Paths.get("src", "main", "resources", "VGIndex"));
        File JSONFile = new File("src/main/resources/attributes.json");
        VisualGenomeIndexer.indexGenomeAttributes(JSONFile, VGDirectory);

        resultTab = new Hashtable<>();
        storedVectors = new Hashtable<>();

        // store WordVec for all unique words within the test file
        /*
        Scanner fileScanner = new Scanner(new File("src/main/resources/truth.txt"));
        while (fileScanner.hasNext()) {
            String[] nextLine = fileScanner.nextLine().split(",");
            for (int i = 0; i < 3; i++) {
                if (!storedVectors.containsKey(nextLine[i])) {
                    storedVectors.put(nextLine[i], new WordVec(nextLine[i], "W2V"));
                    Thread.sleep(500);
                }
            }
        }
        */


        /*
        Double maxScore = -1.0;
        Double bestTresh = -1.0;
        FileWriter logWriter = new FileWriter("src/main/resources/completeGenomeIteration.log");
        */
        // for (Double tresh = 0.0; tresh.compareTo(1.0) < 0; tresh += 0.01) {
        Double tresh = 0.03;
        FileWriter resultWriter = new FileWriter("src/main/resources/VGWNW2V.txt");
        Scanner taskScanner = new Scanner(new File("src/main/resources/trial/ref/truth.txt"));


        // record scores with treshold set to tresh
        while (taskScanner.hasNext()) {
            String[] nextLine = taskScanner.nextLine().split(",");
            resultWriter.write(nextLine[0] + "," + nextLine[1] + "," + nextLine[2] + ","
                    + compare(nextLine[0], nextLine[1], nextLine[2], graph, tresh, "W2V") + "\n");
        }

        resultWriter.close();

            /*
            // evaluate the scores
            Process eval = Runtime.getRuntime().exec("python3 src/main/resources/trial/evaluation.py src/main/resources/trial/ src/main/resources/trial/");
            eval.waitFor();
            Scanner scoreScanner = new Scanner(new File("src/main/resources/trial/scores.txt"));
            Double result;
            try {
                result = scoreScanner.nextDouble();
            } catch (Exception e) {
                result = 0.0;
            }
            logWriter.write("Result for " + tresh + ":" + result + lineSeparator);
            if (Double.compare(result, maxScore) > 0) {
                bestTresh = tresh;
                maxScore = result;
            }
            */

        // }
        /*
        logWriter.write(lineSeparator + "Best performing treshold: " + bestTresh + lineSeparator);
        logWriter.write("Best result: " + maxScore + lineSeparator);
        logWriter.close();
        */

    }

    private static int vectorComparison(String pivot, String comparison, String feature, Double tresh) {
        WordVec pivotVec = storedVectors.get(pivot);
        WordVec comparisonVec = storedVectors.get(comparison);
        WordVec featureVec = storedVectors.get(feature);

        if (pivotVec.getEmbedding().length == 0 | comparisonVec.getEmbedding().length == 0 |
                featureVec.getEmbedding().length == 0) {
            System.err.println("Failed to generate embedding for " + pivot + " " + comparison + " " + feature);
            return 0;
        }

        // calculate feature's compliment to pivot and comparison
        WordVec pivotCompliment = pivotVec.sub(featureVec);
        WordVec comparisonCompliment = comparisonVec.sub(featureVec);

        Double originalSim = pivotVec.cosineSimilarity(comparisonVec);
        Double complimentSim = pivotCompliment.cosineSimilarity(comparisonCompliment);

        if (Double.compare(complimentSim - originalSim, tresh) > 0) {
            return 1;
        }
        return 0;


    }

    private static List<String> getHypernyms(String term) throws JWNLException {

        IndexWord indexWord = dictionary.getIndexWord(POS.NOUN, term);
        List<String> result = new ArrayList<>();

        try {
            Synset[] senses = indexWord.getSenses();
            for (Synset sense : senses) {
                PointerTargetNodeList hypernyms = PointerUtils.getInstance().getDirectHypernyms(sense);
                for (Iterator itr = hypernyms.iterator(); itr.hasNext(); ) {
                    PointerTargetNode node = (PointerTargetNode) itr.next();
                    Synset synset = node.getSynset();
                    for (Word compositeHypernym : synset.getWords()) {
                        String[] hypernymArray = compositeHypernym.getLemma().split("_");
                        for (String hypernym : hypernymArray) {
                            if (!result.contains(hypernym)) {
                                result.add(hypernym);
                            }
                        }
                    }
                }
            }

        } catch (JWNLException e) {
            System.out.println(e);
        }
        return result;

    }

    public static int compare(String pivot, String comparison, String feature, Graph graph, Double tresh, String model) {
        pivot = pivot.toLowerCase();
        comparison = comparison.toLowerCase();
        feature = feature.toLowerCase();

        BooleanQuery.Builder builderPivot = new BooleanQuery.Builder();
        BooleanQuery.Builder builderComparison = new BooleanQuery.Builder();
        builderPivot.add(new TermQuery(new Term("definiendum", pivot)), BooleanClause.Occur.MUST);
        builderPivot.add(new WildcardQuery(new Term("property", "*" + feature + "*")), BooleanClause.Occur.MUST);

        builderComparison.add(new TermQuery(new Term("definiendum", comparison)), BooleanClause.Occur.MUST);
        builderComparison.add(new WildcardQuery(new Term("property", feature + "*")), BooleanClause.Occur.MUST);

        BooleanQuery queryPivot = builderPivot.build();
        BooleanQuery queryComparison = builderComparison.build();

        DirectoryReader reader;
        IndexSearcher searcher;
        try {
            reader = DirectoryReader.open(graphDirectory);
            searcher = new IndexSearcher(reader);
        } catch (IOException e){
            throw new IllegalArgumentException("Invalid WordNet Index directory specified.");
        }

        ScoreDoc[] resultsComparison, resultsPivot;
        try {
            resultsComparison = searcher.search(queryComparison, 10).scoreDocs;
            resultsPivot = searcher.search(queryPivot, 10).scoreDocs;
        }catch (IOException e){
            throw new RuntimeException("Failed to obtain search results for WordNet Index query.");
        }

        Boolean result = resultsPivot.length != 0 && resultsComparison.length == 0;

        if (result) {
            return 1;
        }
        return 0;
    }

    public static int vgComparison(String pivot, String comparison, String feature, Directory VGDirectory) {
        BooleanQuery.Builder builderVGPivot = new BooleanQuery.Builder();
        BooleanQuery.Builder builderVGComparison = new BooleanQuery.Builder();
        builderVGPivot.add(new TermQuery(new Term("name", pivot)), BooleanClause.Occur.MUST);
        builderVGPivot.add(new TermQuery(new Term("attribute", feature)), BooleanClause.Occur.MUST);
        builderVGComparison.add(new TermQuery(new Term("name", comparison)), BooleanClause.Occur.MUST);
        builderVGComparison.add(new TermQuery(new Term("attribute", feature)), BooleanClause.Occur.MUST);

        DirectoryReader readerVG;
        try {
            readerVG = DirectoryReader.open(VGDirectory);
        } catch (IOException e){
            throw new IllegalArgumentException("Invalid Visual Genome Index directory specified.");
        }

        IndexSearcher searcherVG = new IndexSearcher(readerVG);

        BooleanQuery queryVGPivot = builderVGPivot.build();
        BooleanQuery queryVGComparison = builderVGComparison.build();

        ScoreDoc[] resultsVGPivot, resultsVGComparison;
        try {
            resultsVGPivot = searcherVG.search(queryVGPivot, 10).scoreDocs;
            resultsVGComparison = searcherVG.search(queryVGComparison, 10).scoreDocs;
        } catch (IOException e){
            throw new RuntimeException("Failed to obtain search results for Visual Genome Index query.");
        }

        if (resultsVGPivot.length > 0 && resultsVGComparison.length == 0) {
            return 1;
        }
        return 0;
    }



    private static int similarityComparison(String pivot, String comparison, String feature, String model, Double tresh) {
        Double[] similarityArr;
        try {
            similarityArr = getSimilarity(pivot, comparison, feature, model);
        } catch (Exception e){
            throw new IllegalArgumentException("Failed to calculate the cosine similarity for given arguments");
        }
        Double similarity = similarityArr[0] - similarityArr[1];

        if (Double.compare(similarity, tresh) > 0) {
            return 1;
        }
        return 0;
    }

    private static double hypernymSimilarityDiff(String pivot, String comparison, String feature, Graph graph) throws IOException {
        Double diff = similarityDiff(pivot, comparison, feature);

        List<String> pivotHypernyms, comparisonHypernyms;
        try {
            pivotHypernyms = getHypernyms(pivot);
            comparisonHypernyms = getHypernyms(comparison);
        } catch (Exception e) {
            logString += e + lineSeparator;
            return diff;
        }

        logString += "Pivot Hypernym: " + pivotHypernyms + " Comparison Hypernym: " + comparisonHypernyms
                + lineSeparator;

        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://indra.lambda3.org/relatedness");
        httppost.setHeader("Content-Type", "application/json");

// Request parameters and other properties.
        Double pivotHypernymSim = getMaxSim(feature, pivotHypernyms, httpclient, httppost);
        Double comparisonHypernymSim = getMaxSim(feature, comparisonHypernyms, httpclient, httppost);
        Double hypernymDiff = Math.abs(pivotHypernymSim - comparisonHypernymSim);
        logString += "Difference between hypernyms: " + hypernymDiff + lineSeparator;

        if (Double.compare(hypernymDiff, diff) > 0) {
            return hypernymDiff;
        } else {
            return diff;
        }

    }

    private static double getMaxSim(String feature, List<String> hypernymList, HttpClient httpclient, HttpPost httppost) throws IOException {
        String pivotParams = "{\n" +
                "\t\"corpus\": \"googlenews300neg\",\n" +
                "\t\"model\": \"W2V\",\n" +
                "\t\"language\": \"EN\",\n" +
                "\t\"scoreFunction\": \"COSINE\",\n" +
                "\t\"pairs\": [";

        for (int i = 0; i < (hypernymList.size() - 1); i++) {
            pivotParams += "{ \"t1\": \"" + hypernymList.get(i) + "\", \"t2\": \"" + feature + "\"}, ";
        }
        pivotParams += "{ \"t1\": \"" + hypernymList.get(hypernymList.size() - 1) + "\", \"t2\" : \"" + feature + "\"}]}";
        httppost.setEntity(new ByteArrayEntity(pivotParams.getBytes("UTF-8")));

//Execute and get the response.
        HttpResponse response = httpclient.execute(httppost);
        StatusLine status = response.getStatusLine();
        HttpEntity entity = response.getEntity();

        if (entity != null) {
            InputStream instream = entity.getContent();
            try {
                StringWriter writer = new StringWriter();
                IOUtils.copy(instream, writer, "UTF-8");
                String theString = writer.toString();
                JSONArray pairs = new JSONObject(theString).getJSONArray("pairs");

                String maxHypernym = pairs.getJSONObject(0).get("t1").toString();
                Double pivotMaxSim = pairs.getJSONObject(0).getDouble("score");
                for (int i = 1; i < hypernymList.size(); i++) {
                    Double currentSim = pairs.getJSONObject(i).getDouble("score");
                    if (Double.compare(pivotMaxSim, currentSim) < 0) {
                        pivotMaxSim = currentSim;
                        maxHypernym = pairs.getJSONObject(i).get("t1").toString();
                    }
                }
                logString += "Hypernym with the highest similarity to feature: " + maxHypernym + "(" + pivotMaxSim + ")"
                        + lineSeparator;
                return pivotMaxSim;

            } catch (Exception e) {
                System.out.println(status);
                System.out.println(e);
            } finally {
                instream.close();
            }
        }
        return 0;
    }

    private static double similarityDiff(String pivot, String comparison, String feature) throws IOException {
        /*
        try {
            Thread.sleep(500);
        }catch (InterruptedException e){}

        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://indra.lambda3.org/relatedness");
        httppost.setHeader("Content-Type", "application/json");

// Request parameters and other properties.
        String params = "{\n" +
                "\t\"corpus\": \"googlenews300neg\",\n" +
                "\t\"model\": \"W2V\",\n" +
                "\t\"language\": \"EN\",\n" +
                "\t\"scoreFunction\": \"COSINE\",\n" +
                "\t\"pairs\": [{\n" +
                "\t\t\"t1\": \"" + pivot + "\",\n" +
                "\t\t\"t2\": \"" + feature + "\"\n" +
                "\t},\n" +
                "\t{\n" +
                "\t\t\"t1\": \"" + comparison + "\",\n" +
                "\t\t\"t2\": \"" + feature + "\"\n" +
                "\t}]\n" +
                "}";


        httppost.setEntity(new ByteArrayEntity(params.getBytes("UTF-8")));

//Execute and get the response.
        HttpResponse response = httpclient.execute(httppost);
        StatusLine status = response.getStatusLine();
        HttpEntity entity = response.getEntity();
        */

        try {
                /*
                StringWriter writer = new StringWriter();
                IOUtils.copy(instream, writer, "UTF-8");
                String theString = writer.toString();
                JSONArray pairs = new JSONObject(theString).getJSONArray("pairs");

                // note: the similarity between last request is served first
                Double similarity1 = pairs.getJSONObject(1).getDouble("score");
                */
            Double[] similarity = getSimilarity(pivot, comparison, feature, "LSA");
            logString += "LSA similarity" + lineSeparator;
            logString += "Similarity score between " + pivot + " and "
                    + feature + " is " + similarity[0]
                    + lineSeparator;

            logString += "Similarity score between " + comparison + " and "
                    + feature + " is " + similarity[1]
                    + lineSeparator;

            logString += "The difference between similarity scores is " + (similarity[0] - similarity[1])
                    + lineSeparator;


            similarity = getSimilarity(pivot, comparison, feature, "ESA");
            logString += "ESA similarity" + lineSeparator;
            logString += "Similarity score between " + pivot + " and "
                    + feature + " is " + similarity[0]
                    + lineSeparator;

            logString += "Similarity score between " + comparison + " and "
                    + feature + " is " + similarity[1]
                    + lineSeparator;

            logString += "The difference between similarity scores is " + (similarity[0] - similarity[1])
                    + lineSeparator;

            similarity = getSimilarity(pivot, comparison, feature, "GloVe");
            logString += "GloVe similarity" + lineSeparator;
            logString += "Similarity score between " + pivot + " and "
                    + feature + " is " + similarity[0]
                    + lineSeparator;

            logString += "Similarity score between " + comparison + " and "
                    + feature + " is " + similarity[1]
                    + lineSeparator;

            logString += "The difference between similarity scores is " + (similarity[0] - similarity[1])
                    + lineSeparator;

            similarity = getSimilarity(pivot, comparison, feature, "W2V");
            logString += "W2V similarity" + lineSeparator;
            logString += "Similarity score between " + pivot + " and "
                    + feature + " is " + similarity[0]
                    + lineSeparator;

            logString += "Similarity score between " + comparison + " and "
                    + feature + " is " + similarity[1]
                    + lineSeparator;

            logString += "The difference between similarity scores is " + (similarity[0] - similarity[1])
                    + lineSeparator;

            logString += "10 closest neighbours:" + lineSeparator;
            logString += getNeighbours(pivot, comparison, feature, 10);
            logString += lineSeparator;
            return similarity[0] - similarity[1];
        } catch (Exception e) {
            logString += "While finding similarity scores for " + pivot + " " + comparison
                    + " " + feature + " exception occurred " + e + lineSeparator;
            e.printStackTrace();
            System.out.println(pivot + " " + comparison + " " + feature);
        }

        return 0;

    }

    static String getNeighbours(String pivot, String comparison, String feature, int k) throws Exception {
        Thread.sleep(500);
        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://indra.lambda3.org/neighbors/relatedness");
        httppost.setHeader("Content-Type", "application/json");

// Request parameters and other properties.
        String params = "{\n" +
                "        \"corpus\": \"wiki-2014\",\n" +
                "        \"model\": \"W2V\",\n" +
                "        \"language\": \"EN\",\n" +
                "        \"topk\": " + k + ",\n" +
                "\"scoreFunction\": \"COSINE\",\n" +
                "        \"terms\" : [\"" + pivot + "\", \"" + comparison + "\", \"" + feature + "\"] \n" +
                "}";
        httppost.setEntity(new ByteArrayEntity(params.getBytes("UTF-8")));

//Execute and get the response.
        HttpResponse response = httpclient.execute(httppost);
        HttpEntity entity = response.getEntity();

        if (entity != null) {
            InputStream instream = entity.getContent();
            StringWriter writer = new StringWriter();
            IOUtils.copy(instream, writer, "UTF-8");
            return writer.toString();
        }
        return "";
    }

    private static Double[] getSimilarity(String pivot, String comparison, String feature, String model) throws Exception{
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }

        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://indra.lambda3.org/relatedness");
        httppost.setHeader("Content-Type", "application/json");


        String params = "{\n" +
                "\t\"corpus\": \"wiki-2014\",\n" +
                "\t\"model\": \"" + model + "\",\n" +
                "\t\"language\": \"EN\",\n" +
                "\t\"scoreFunction\": \"COSINE\",\n" +
                "\t\"pairs\": [{\n" +
                "\t\t\"t1\": \"" + pivot + "\",\n" +
                "\t\t\"t2\": \"" + feature + "\"\n" +
                "\t},\n" +
                "\t{\n" +
                "\t\t\"t1\": \"" + comparison + "\",\n" +
                "\t\t\"t2\": \"" + feature + "\"\n" +
                "\t}]\n" +
                "}";


        httppost.setEntity(new ByteArrayEntity(params.getBytes("UTF-8")));

//Execute and get the response.
        HttpResponse response = httpclient.execute(httppost);
        HttpEntity entity = response.getEntity();

        InputStream instream = entity.getContent();
        StringWriter writer = new StringWriter();
        IOUtils.copy(instream, writer, "UTF-8");
        String theString = writer.toString();
        JSONArray pairs = new JSONObject(theString).getJSONArray("pairs");
        instream.close();
        Double pivotSim, comparisonSim;
        if (pairs.getJSONObject(0).getString("t1").equals(pivot)) {
            pivotSim = pairs.getJSONObject(0).getDouble("score");
            comparisonSim = pairs.getJSONObject(1).getDouble("score");
        } else {
            pivotSim = pairs.getJSONObject(1).getDouble("score");
            comparisonSim = pairs.getJSONObject(0).getDouble("score");
        }
        return new Double[]{pivotSim, comparisonSim};
    }

    /**
     * Checks whether <code>test</code> can be found within k nearest neighbours of <code>center</code>.
     *
     * @param center term for which k nearest neighbours are found
     * @param test   term for which to search for amongst the k nearest neighbours
     * @throws IOException
     * @throws JSONException
     */
    private static boolean isFoundInKNN(String center, String test, String model, int k) throws IOException {
        HttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("http://indra.lambda3.org/neighbors/relatedness");
        httppost.setHeader("Content-Type", "application/json");

// Request parameters and other properties.
        String params = "{\n" +
                "        \"corpus\": \"wiki-2014\",\n" +
                "        \"model\": \"" + model + "\",\n" +
                "        \"language\": \"EN\",\n" +
                "        \"topk\": " + k + ",\n" +
                "\"scoreFunction\": \"COSINE\",\n" +
                "        \"terms\" : [\"" + center + "\"]\n" +
                "}";
        httppost.setEntity(new ByteArrayEntity(params.getBytes("UTF-8")));

//Execute and get the response.
        HttpResponse response = httpclient.execute(httppost);
        StatusLine status = response.getStatusLine();
        HttpEntity entity = response.getEntity();

        if (entity != null) {
            InputStream instream = entity.getContent();
            try {
                StringWriter writer = new StringWriter();
                IOUtils.copy(instream, writer, "UTF-8");
                String theString = writer.toString();
                Iterator key = new JSONObject(theString).getJSONObject("terms").getJSONObject(center).keys();
                while (key.hasNext()) {
                    if (key.next().toString().equals(test)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                System.out.println(center + " " + test);
            } finally {
                instream.close();
            }
        }
        return false;
    }
}
