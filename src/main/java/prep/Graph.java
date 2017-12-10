package prep;

import org.apache.jena.rdf.model.*;

import java.io.InputStream;

public class Graph {
    private final Model model;
    private final String resourceURIStart = "http://nlp/resources/synsets/WordNetNounSynset#";

    public Graph(){
        this.model = ModelFactory.createDefaultModel();
    }

    public void addModel(String graphLocation){
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream graphFile = loader.getResourceAsStream(graphLocation);
        // InputStream graphFile = FileManager.get().open(graphLocation);
        if (graphFile == null){
            throw new IllegalArgumentException("File: " + graphLocation + " not found");
        }
        this.model.read(graphLocation);
    }

    public Definition findDefinition(String definendumName){
        return new Definition(this.model.getResource(this.resourceURIStart + definendumName));
    }
}
