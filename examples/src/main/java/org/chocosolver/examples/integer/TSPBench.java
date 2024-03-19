package org.chocosolver.examples.integer;

import org.chocosolver.parser.RegParser;
import org.chocosolver.parser.SetUpException;
import org.chocosolver.parser.xcsp.XCSP;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.SearchParams;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainLast;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainReverseBest;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.selectors.variables.DomOverWDeg;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.variables.IntVar;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TSPBench extends BenchParser {

    public IntVar[] succ;
    public Map<IntVar, Integer> indices;
    public TSPInstance tspInstance;

    public class TSPInstance {

        int max;
        int[][] D; // matrix of distances
        int C; // number of cities

        /**
         * Read TSP Instance from xml
         * See http://comopt.ifi.uni-heidelberg.de/software/TSPLIB95/XML-TSPLIB/Description.pdf
         * @param xmlPath path to the file
         */
        public TSPInstance (String xmlPath) {
            // Instantiate the Factory
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            int obj = -1;
            max = Integer.MIN_VALUE;
            try {

                // optional, but recommended
                // process XML securely, avoid attacks like XML External Entities (XXE)
                dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

                // parse XML file
                DocumentBuilder db = dbf.newDocumentBuilder();

                Document doc = db.parse(new File(xmlPath));
                doc.getDocumentElement().normalize();

                NodeList objlist = doc.getElementsByTagName("objective");
                if (objlist.getLength() > 0) {
                    obj = Integer.parseInt(objlist.item(0).getTextContent());
                }

                NodeList list = doc.getElementsByTagName("vertex");

                C = list.getLength();
                D = new int[C][C];

                for (int i = 0; i < C; i++) {
                    NodeList edgeList = list.item(i).getChildNodes();
                    for (int v = 0; v < edgeList.getLength(); v++) {

                        Node node = edgeList.item(v);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element element = (Element) node;
                            String cost = element.getAttribute("cost");
                            String adjacentNode = element.getTextContent();
                            int j = Integer.parseInt(adjacentNode);
                            D[i][j] = (int) Math.rint(Double.parseDouble(cost));
                            max = Math.max(max, D[i][j]);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    @Override
    public void buildModel() {
        super.buildModel();
    }

    @Override
    public Model makeModel() {
        int C = tspInstance.C;
        int[][] D = tspInstance.D;
        // A new model instance
        Model model = new Model("TSP");
        int max = 999;
        indices = new HashMap<>();
        // VARIABLES
        // For each city, the next one visited in the route
        succ = model.intVarArray("succ", C, 0, C - 1);
        // For each city, the distance to the succ visited one
        IntVar[] dist = model.intVarArray("dist", C, 0, max);
        // Total distance of the route
        IntVar totDist = model.intVar("Total distance", 0, max * C);

        // CONSTRAINTS
        for (int i = 0; i < C; i++) {
            // For each city, the distance to the next one should be maintained
            // this is achieved, here, with a TABLE constraint
            // Such table is inputed with a Tuples object
            // that stores all possible combinations
            Tuples tuples = new Tuples(true);
            for (int j = 0; j < C; j++) {
                // For a given city i
                // a couple is made of a city j and the distance i and j
                if (j != i) tuples.add(j, D[i][j]);
            }
            // The Table constraint ensures that one combination holds
            // in a solution
            model.table(succ[i], dist[i], tuples).post();
            indices.put(succ[i], i);
        }
        // The route forms a single circuit of size C, visiting all cities
        model.subCircuit(succ, 0, model.intVar(C)).post();
        // Defining the total distance
        model.sum(dist, "=", totDist).post();

        model.setObjective(Model.MINIMIZE, totDist);
        return model;
    }

    @Override
    public IntVar[] decisionVars() {
        return succ;
    }

    public TSPBench() {
        super();
    }

    @Override
    public IntValueSelector makeGreedy() {
        return new Greedy();
    }
    
    private class Greedy implements IntValueSelector {

        @Override
        public int selectValue(IntVar var) throws ContradictionException {
            int index = indices.get(var);
            // look for the closest successor
            int closest = var.getLB();
            int smallestDist = Integer.MAX_VALUE;
            int ub = var.getUB();
            for (int v = var.getLB(); v <= ub; v = var.nextValue(v)) {
                int dist = tspInstance.D[index][v];
                if (dist < smallestDist) {
                    closest = v;
                    smallestDist = dist;
                }
            }
            return closest;
        }
    }

    @Override
    public boolean setUp(String... args) throws SetUpException {
        boolean setup = super.setUp(args);
        this.tspInstance = new TSPInstance(this.instance);
        return setup;
    }

    // -valsel GREEDY,Best,1,true
    public static void main(String[] args) {
        try {
            TSPBench bench = new TSPBench();
            if (bench.setUp(args)) {
                bench.createSolver();
                bench.buildModel();
                bench.configureSearch();
                bench.singleThread();
            }
        } catch (Exception e) {
            String input = Arrays.stream(args).map(s -> s.replace(",", ";")).collect(Collectors.joining(" ")).replace(", ", " ");
            System.out.println("Error with input " + input);
            e.printStackTrace();
            System.exit(1);
        }
    }

}
