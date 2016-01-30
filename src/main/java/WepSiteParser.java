import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by mohannad on 29/01/16.
 */
public class WepSiteParser {
    private final static String WEBSITE = "http://www.imdb.com";
    private final static String Movies_OWL = "http://www.mohannadkarim.com/2016/movies.owl";
    private final static String NS = Movies_OWL + "#";
    private final static String MOVIE = Movies_OWL + "/movie" + "#";
    private final static String PERSON = Movies_OWL + "/person" + "#";
    private final static String GENRE = Movies_OWL + "/genre" + "#";
    private final static String COMPANY = Movies_OWL + "/company" + "#";

    private final static String DBR = "http://dbpedia.org/resource/";
    private final static String DBR_FR = "http://fr.dbpedia.org/resource/";

    private final static String REF_ONT = "movies.owl";
    private final static String OUTPUT = "movies.rdf";

    private static Hashtable<String, String> GENRE_MAP = new Hashtable<>() ;

    private final OntModel m;

    public WepSiteParser() {
        // 1- load movies ontology file
        System.out.println("1- load Movies ontology file ....");
        m = ModelFactory.createOntologyModel();
        try {
            m.read(FileManager.get().open(REF_ONT), "", "RDF/XML");
        } catch(NullPointerException e) {
            System.out.print("An error happened while reading the ontology file: ");
            e.printStackTrace();
        }
        m.setNsPrefix("movies", NS);
        m.setNsPrefix("movies-m", MOVIE);
        m.setNsPrefix("movies-p", PERSON);
        m.setNsPrefix("movies-g", GENRE);
        m.setNsPrefix("movies-c", COMPANY);
    }

    private void save() throws FileNotFoundException {
        m.write(new FileOutputStream(OUTPUT), "RDF/XML");
    }

    private void parseIndex(int i) throws IOException {
        //Document doc = Jsoup.connect(WEBSITE+"/chart/moviemeter?ref_=nv_mv_mpm_7").get();
        //Document doc = Jsoup.connect(WEBSITE+"/chart/top/?ref_=nv_mv_250_6").userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:21.0) Gecko/20100101 Firefox/21.0")
        //.header("Accept-Language", "en").get();
        org.jsoup.Connection connection = Jsoup
                .connect("http://www.movies.com/dvd-movies?pn="+i).userAgent("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:43.0) Gecko/20100101 Firefox/43.0")
                .header("Accept-Language","en").ignoreHttpErrors(true);
        System.out.println(connection.execute().url());
        Document doc = connection.get();
        //Elements movies = doc.select(".lister-list td[class=titleColumn] a");
        Elements movies = doc.select(".results a");

        Set<String> urls = new HashSet<String>();
        for(Element movie : movies) {
            urls.add(movie.attr("href"));
            System.out.println(movie.attr("href"));
            //break;
        }

        System.out.println(urls.size());
        for(String url: urls) {
            parseMovie(url);
        }
    }

    private void parseMovie(String url) {
        Document doc;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        List<Set<String>> people = null;

        String movietitle = doc.select("#completeInfo h1").first().text().trim();
        System.out.println("Movie: " + movietitle);

        //create resource movie with the type 'Movie' and data property 'title'
        Resource movie = m.createResource(MOVIE + movietitle.replace(" ","_"));
        movie.addProperty(RDF.type, m.getProperty(NS + "Movie"))
                .addProperty(m.getProperty(NS + "title"), movietitle);

        // does it have corresponding dbpedia resource?
        String mResult;
        if((mResult = SparqlQuery.movieInDbpediaEn(movietitle)) != null) {
            System.out.println("mResult: " + mResult);
            movie.addProperty(OWL.sameAs, mResult);

            //add movie details to the RDF model
            //details available: runtime, distributor, studio, budget, country, abstract
            HashMap<String, String> mDetails = SparqlQuery.getMovieInfo(mResult);

            //get runtime
            if(mDetails.containsKey("runtime")) {
                movie.addProperty(m.getProperty(NS + "duration"), mDetails.get("runtime"), XSDDatatype.XSDdouble);
                System.out.println("\tDuration: " + mDetails.get("runtime"));
            }

            //get release date
            if(mDetails.containsKey("releaseDate")) {
                String releaseDate = mDetails.get("releaseDate");
                if(releaseDate.contains(","))
                    releaseDate = releaseDate.split(",")[0];
                if(releaseDate.trim().matches("^[0-9]{4}$"))
                    releaseDate = releaseDate.trim() + "-01-01";
                movie.addProperty(m.getProperty(NS + "releaseDate"), releaseDate + "T00:00:00", XSDDatatype.XSDdateTime);
                System.out.println("\tRelease date: " + releaseDate);
            }

            //get budget
            if(mDetails.containsKey("budget")) {
                movie.addProperty(m.getProperty(NS + "budget"), mDetails.get("budget"), XSDDatatype.XSDdouble);
                System.out.println("\tBudget: " + mDetails.get("budget"));

            }
            //get country
            if(mDetails.containsKey("country")) {
                if(mDetails.get("country").contains("*")) { //multiple countries
                    String[] countries = mDetails.get("country").split("\\*");
                    for (String country : countries) {
                        if(!country.isEmpty()) {
                            movie.addProperty(m.getProperty(NS + "country"), country.trim());
                            System.out.println("\tCountry: " + country.trim());
                        }
                    }
                } else {
                    movie.addProperty(m.getProperty(NS + "country"), mDetails.get("country"));
                    System.out.println("\tCountry: " + mDetails.get("country"));
                }
            }

            //get distributor
            if(mDetails.containsKey("distributor")) {
                System.out.println("\tDistributor: " + mDetails.get("distributor"));
                Resource distributor = m.createResource(COMPANY + mDetails.get("distributor").replace("http://dbpedia.org/resource/", ""));
                distributor.addProperty(RDF.type, m.getProperty(NS + "Company"))
                        .addProperty(m.getProperty(NS + "companyName"), mDetails.get("distributor").replace("http://dbpedia.org/resource/", "").replace("_", " "))
                        .addProperty(OWL.sameAs, mDetails.get("distributor"));
                movie.addProperty(m.getProperty(NS + "isDistributedBy"), distributor);
            }

            //get studio
            if(mDetails.containsKey("studio")) {
                System.out.println("\tStudio: " + mDetails.get("studio"));
                Resource studio = m.createResource(COMPANY + mDetails.get("studio").replace("http://dbpedia.org/resource/", ""));
                studio.addProperty(RDF.type, m.getProperty(NS + "Company"))
                        .addProperty(m.getProperty(NS + "companyName"), mDetails.get("studio").replace("http://dbpedia.org/resource/", "").replace("_", " "))
                        .addProperty(OWL.sameAs, mDetails.get("studio"));
                movie.addProperty(m.getProperty(NS + "isProducedBy"), studio);
            }

            if(mDetails.containsKey("abstract")) {
                String abs = mDetails.get("abstract");
                movie.addProperty(m.getProperty(NS + "abstract"), abs);
                System.out.println("\tAbstract: " + abs);
            }

            people = SparqlQuery.getPerson(mResult);
        }

        Elements sidebar = doc.select("#movieSpecs li");
        for (Element element:sidebar) {
            switch (element.select("span").text()) {
                case "Genres:" :
                    String[] genres = element.text().substring(8).split(", |\\/");
                    for(String genreName : genres){
                        if(GENRE_MAP.containsKey(genreName))
                            genreName = GENRE_MAP.get(genreName);
                        else { //unknown genre; report it and create new resource to acommodate
                            System.out.println(">>>) another genre found: " + genreName);
                            GENRE_MAP.put(genreName,genreName);
                            m.createResource(GENRE + genreName)
                                    .addProperty(RDF.type, m.getProperty(NS + "Genre"));
                        }
                        movie.addProperty(m.getProperty(NS + "hasGenre"), m.getResource(GENRE + genreName));
                    }
                    break;
                case "Director:" :
                    String[] directors = element.text().substring(9).split(", |\\/");
                    for(String director : directors){
                        //System.out.println("DDDD : " + director);
                        Resource person = m.createResource(PERSON + director.replace(" ", "_"));
                        person.addProperty(m.getProperty(NS + "name"), director);
                        person.addProperty(RDF.type, m.getProperty(NS + "Person"));
                        movie.addProperty(m.getProperty(NS + "hasDirector"), person);

                        //check if the director has dbpedia page. Describe as sameAs if true
                        String qResult;
                        if((qResult = SparqlQuery.personInDbpediaEn(director)) != null) { // in dbpedia.org
                            person.addProperty(OWL.sameAs, DBR + qResult);
                        }
                    }
                    break;
                case "Cast:":

                    String cast= element.text().substring(5 , element.text().lastIndexOf('.'));
                    String[] actors = cast.split(", |\\/");
                    for(String actor : actors){
                        Resource person = m.createResource(PERSON + actor.trim().replace(" ", "_"));
                        person.addProperty(RDF.type, m.getProperty(NS + "Person"))
                                .addProperty(m.getProperty(NS + "name"), actor.trim());
                        movie.addProperty(m.getProperty(NS + "hasActor"), person);

                        //check if the actor has dbpedia page. Describe as sameAs if true
                        String qResult;
                        if((qResult = SparqlQuery.personInDbpediaEn(actor)) != null) { // in dbpedia.org
                            person.addProperty(OWL.sameAs, DBR + qResult);
                        }

                    }
                    break;

            }
        }



    }

    public static void main(String args []) throws IOException {
        WepSiteParser wp = new WepSiteParser();
        for(int i=5 ; i <6 ; i++)
            wp.parseIndex(i);
        wp.save();
        System.out.println("done !!!");
    }
}
