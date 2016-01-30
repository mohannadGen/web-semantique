import org.apache.jena.query.*;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetImpl;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by mohannad on 29/01/16.
 */
public class SparqlQuery {

    final static String OWL 		= "PREFIX owl: <http://www.w3.org/2002/07/owl#> ";
    final static String XSD 		= "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>";
    final static String RDFS 		= "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>";
    final static String RDF 		= "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> ";
    final static String FOAF 		= "PREFIX foaf: <http://xmlns.com/foaf/0.1/> ";
    final static String DC 			= "PREFIX dc: <http://purl.org/dc/elements/1.1/> ";
    final static String DEFAULT 	= "PREFIX : <http://dbpedia.org/resource/> ";
    final static String DBPEDIA2 	= "PREFIX dbpedia2: <http://dbpedia.org/property/> ";
    final static String DBPEDIA 	= "PREFIX dbpedia: <http://dbpedia.org/> ";
    final static String SKOS 		= "PREFIX skos: <http://www.w3.org/2004/02/skos/core#> ";
    final static String DBO 		= "PREFIX dbo: <http://dbpedia.org/ontology/>";


    final static String PREFIXES_EN = OWL + XSD + RDFS + RDF + FOAF + DC + DEFAULT +
            DBPEDIA + DBPEDIA2 + SKOS + DBO;


    public static String personInDbpediaEn(String personName) {
       // personName = personName.replace(" ", "_");
        System.out.println(" ---))) is "+ personName + " in dbpedia ");
        String queryString = PREFIXES_EN +
                "SELECT DISTINCT ?person WHERE {" +
                "SERVICE <http://DBpedia.org/sparql> "+
                "{ " +
                "?person a dbo:Person ." +
                "?person rdfs:label ?name ." +
                "filter regex( str(?name), \"^" + personName  + "$\", \"i\") " +
                "}" +
                "} LIMIT 5";
        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, new DatasetImpl(ModelFactory.createDefaultModel()));
        ResultSet results = qe.execSelect();

        List<String> itemList = new ArrayList<>();
        int count = 0;
        while(results.hasNext()) {
            QuerySolution sol = results.nextSolution();
            itemList.add(sol.getResource("person").getLocalName()); //for debugging purposes
            count++;
        }
        if(count == 1) {
            return itemList.get(0);
        } else if (count > 1) {
            System.out.println("-- found more than one resource in dbpedia for: " + personName);
            for(String item : itemList)
                System.out.print(item + "-\t");
            System.out.println();
        }
        //otherwise, not found
        return null;

    }


    public static String movieInDbpediaEn(String moviename) {
        //sanitize the title: remove "3D"
        moviename = moviename.replace(" 3D","").replace(" (NL)", "");
        System.out.println("-- querying movie: " + moviename);

        //just in case there are more than one resources with the same label
        String mn = moviename.replace(" ", "_");
        Pattern p = Pattern.compile(".*" + mn + "_\\(film\\)$|.*" + mn + "_\\(201\\d_film\\)$", Pattern.CASE_INSENSITIVE); //ends with either "(film)" or "(201*_film)"

        String queryString = PREFIXES_EN +
                "SELECT DISTINCT ?movie WHERE {" +
                "SERVICE <http://DBpedia.org/sparql> "+
                "{ " +
                "?movie a dbo:Film ." +
                "?movie rdfs:label ?title ." +
                "filter regex( str(?title), \"^" + moviename  + "\", \"i\") " +
                "}" +
                "} LIMIT 15";
        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, new DatasetImpl(ModelFactory.createDefaultModel()));
        ResultSet results = qe.execSelect();

        //querying movie is a little bit tricky. Dbpedia has inconsistent labeling for movies. For example, old movies/franchises/movies which using popular words as title
        //uses suffix "(film)". Others simply don't use it.

        List<String> itemList = new ArrayList<>();
        int count = 0;
        while(results.hasNext()) {
            QuerySolution sol = results.nextSolution();
            Resource movie = sol.getResource("movie");
            itemList.add(sol.getResource("movie").getURI());
            count++;
        }

        if(count == 1) {
            System.out.print(" found.\n");
            return itemList.get(0);
        } if(count > 1) {
            System.out.println("-- crap, got multiple results for movie.");
            //see if it ends with "(film)" or "(201x film)"
            for(String item : itemList) {
                if(p.matcher(item).find())
                    return item;
            }
        }
        System.out.print(" movie not found.\n");
        return null;
    }

    public static HashMap<String, String> getMovieInfo(String mRes) {
        HashMap<String, String> results = new HashMap<>();

        String queryString = PREFIXES_EN +
                "SELECT DISTINCT ?runtime ?distributor ?studio ?budget ?country ?abstract WHERE {"+
                "SERVICE <http://DBpedia.org/sparql> "+
                "{ " +
                "OPTIONAL { <" + mRes + "> dbo:Work\\/runtime ?rt . " +
                "BIND (str(?rt) AS ?runtime)" +
                "}" +
                "OPTIONAL { <" + mRes + "> dbo:distributor ?distributor . }" +
                "OPTIONAL { <" + mRes + "> dbpedia2:studio ?studio . }" +
                "OPTIONAL { <" + mRes + "> dbo:budget ?bud . " +
                "BIND (str(?bud) AS ?budget)" +
                "}" +
                "OPTIONAL { <" + mRes + "> dbpedia2:country ?c .  " +
                "FILTER(lang(?c) = \"en\")" +
                "BIND (str(?c) AS ?country)" +
                "}" +
                "OPTIONAL { <" + mRes + "> dbo:abstract ?abs .  " +
                "FILTER(lang(?abs) = \"en\")" +
                "BIND (str(?abs) AS ?abstract)" +
                "}" +
                " } " +
                "}";

        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, new DatasetImpl(ModelFactory.createDefaultModel()));
        ResultSet rs = qe.execSelect();

        while(rs.hasNext()) {
            QuerySolution sol = rs.nextSolution();
            if(sol.contains("runtime"))
                results.put("runtime", sol.getLiteral("runtime").toString());
            if(sol.contains("distributor"))
                results.put("distributor", sol.getResource("distributor").getURI());
            if(sol.contains("studio")) {
                try {
                    results.put("studio", sol.getResource("studio").getURI());
                } catch(Exception e) {
                    System.out.println("!! [Error]: studio: " + e);
                }

            }
            if(sol.contains("budget"))
                results.put("budget", sol.getLiteral("budget").toString());
            if(sol.contains("country"))
                results.put("country", sol.getLiteral("country").toString());
            if(sol.contains("abstract"))
                results.put("abstract", sol.getLiteral("abstract").toString());
        }

        return results;
    }

    public static HashMap<String, String> getPersonalInfo(String pRes) {
        HashMap<String, String> results = new HashMap<>();
        String queryString = PREFIXES_EN +
                "SELECT DISTINCT ?birthdate ?nationality WHERE {"+
                "SERVICE <http://DBpedia.org/sparql> "+
                "{ " +
                "OPTIONAL { <" + pRes + "> dbo:birthDate ?bd . " +
                "BIND(str(?bd) AS ?birthdate)" +
                "}" +
                "OPTIONAL { <" + pRes + "> dbpedia2:nationality ?nat . "+
                "FILTER(lang(?nat) = \"en\")" +
                "BIND(str(?nat) AS ?nationality)" +
                "}" +
                "} " +
                "}";

        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, new DatasetImpl(ModelFactory.createDefaultModel()));
        ResultSet rs = qe.execSelect();

        while(rs.hasNext()) {
            QuerySolution sol = rs.nextSolution();
            if(sol.contains("birthdate"))
                results.put("birthdate", sol.getLiteral("birthdate").toString());
            if(sol.contains("nationality"))
                results.put("nationality", sol.getLiteral("nationality").toString());
        }

        return results;
    }

    public static List<Set<String>> getPerson(String mRes) {
        HashSet<String> directors = new HashSet<>();
        HashSet<String> actors = new HashSet<>();
        HashSet<String> producers = new HashSet<>();
        HashSet<String> composers = new HashSet<>();
        HashSet<String> editors = new HashSet<>();
        HashSet<String> writers = new HashSet<>();
        HashSet<String> cinematographers = new HashSet<>();

        List<Set<String>> results = new ArrayList<>();

        String queryString = PREFIXES_EN +
                "SELECT DISTINCT ?actor ?director ?producer ?composer ?editor ?writer ?cinematographer WHERE {"+
                "SERVICE <http://DBpedia.org/sparql> "+
                "{ " +
                "OPTIONAL { <" + mRes + "> dbo:starring ?actor . }" +
                "OPTIONAL { <" + mRes + "> dbo:director ?director . }" +
                "OPTIONAL { <" + mRes + "> dbo:producer ?producer . }" +
                "OPTIONAL { <" + mRes + "> dbo:musicComposer ?composer . }" +
                "OPTIONAL { <" + mRes + "> dbo:editing ?editor . }" +
                "OPTIONAL { <" + mRes + "> dbo:writer ?writer . }" +
                "OPTIONAL { <" + mRes + "> dbo:cinematography ?cinematographer . }" +
                " } " +
                "}";
        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, new DatasetImpl(ModelFactory.createDefaultModel()));
        ResultSet rs = qe.execSelect();

        while(rs.hasNext()) {
            QuerySolution sol = rs.nextSolution();
            if(sol.contains("director"))
                directors.add(sol.getResource("director").getURI());
            if(sol.contains("actor"))
                actors.add(sol.getResource("actor").getURI());
            if(sol.contains("producer"))
                producers.add(sol.getResource("producer").getURI());
            if(sol.contains("composer"))
                composers.add(sol.getResource("composer").getURI());
            if(sol.contains("writer"))
                writers.add(sol.getResource("writer").getURI());
            if(sol.contains("cinematographer")) {
                try {
                    cinematographers.add(sol.getResource("cinematographer").getURI());
                } catch(ClassCastException e) {
                    System.out.println("!! [Error]: cannot cast Literal to Resource: " + e);
                }
            }
        }

        results.add(directors);
        results.add(actors);
        results.add(producers);
        results.add(composers);
        results.add(editors);
        results.add(writers);
        results.add(cinematographers);

        return results;
    }

}

