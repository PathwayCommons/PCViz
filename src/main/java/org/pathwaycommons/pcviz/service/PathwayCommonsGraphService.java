package org.pathwaycommons.pcviz.service;

import flexjson.JSONSerializer;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.pattern.miner.*;
import org.pathwaycommons.pcviz.cocitation.CocitationManager;
import org.pathwaycommons.pcviz.model.CytoscapeJsEdge;
import org.pathwaycommons.pcviz.model.CytoscapeJsGraph;
import org.pathwaycommons.pcviz.model.CytoscapeJsNode;
import org.pathwaycommons.pcviz.model.PropertyKey;
import org.springframework.cache.annotation.Cacheable;

import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class PathwayCommonsGraphService {
    private String pathwayCommonsUrl;

    public String getPathwayCommonsUrl() {
        return pathwayCommonsUrl;
    }

    public void setPathwayCommonsUrl(String pathwayCommonsUrl) {
        this.pathwayCommonsUrl = pathwayCommonsUrl;
    }

    /**
     * Cache for co-citations.
     */
    private static Map<String, Map<String, Integer>> cocitationMap = new HashMap<String, Map<String, Integer>>();

    /**
     * Accessor for new co-citations.
     */
    private CocitationManager cocitMan;

    public static Map<String, Map<String, Integer>> getCocitationMap() {
        return cocitationMap;
    }

    public static void setCocitationMap(Map<String, Map<String, Integer>> cocitationMap) {
        PathwayCommonsGraphService.cocitationMap = cocitationMap;
    }

    public CocitationManager getCocitMan() {
        return cocitMan;
    }

    public void setCocitMan(CocitationManager cocitMan) {
        this.cocitMan = cocitMan;
    }

    public PathwayCommonsGraphService(String pathwayCommonsUrl, CocitationManager cocitMan) {
        this.pathwayCommonsUrl = pathwayCommonsUrl;
        this.cocitMan = cocitMan;
    }

    public PathwayCommonsGraphService() {
    }

    @Cacheable("networkCache")
    public String createNetwork(String type, String genes) {
        String networkJson;
        JSONSerializer jsonSerializer = new JSONSerializer().exclude("*.class");
        CytoscapeJsGraph graph = new CytoscapeJsGraph();

        // TODO: Use cpath2 client for this
        String biopaxUrl = getPathwayCommonsUrl() + "/graph?";
        for (String gene : genes.split(","))
        {
            biopaxUrl += "source=" + gene + "&";
        }
        biopaxUrl += "kind=neighborhood";

        HashSet<String> nodeNames = new HashSet<String>();

        SimpleIOHandler ioHandler = new SimpleIOHandler();
        try
        {
            URL url = new URL(biopaxUrl);
            URLConnection urlConnection = url.openConnection();
            Model model = ioHandler.convertFromOWL(urlConnection.getInputStream());

            // the Pattern framework can generate SIF too
            SIFSearcher searcher = new SIFSearcher(
                    new ControlsStateChangeMiner(),
                    new ControlsStateChangeButIsParticipantMiner(),
                    new ConsecutiveCatalysisMiner(null), // todo pass black list here
    //				new InSameComplexMiner(), // add this line after implementing ranking
                    new DegradesMiner()
            );

            for (SIFInteraction sif : searcher.searchSIF(model))
            {
                String srcName = sif.source;
                String targetName = sif.target;

                nodeNames.add(srcName);
                nodeNames.add(targetName);

                CytoscapeJsEdge edge = new CytoscapeJsEdge();
                edge.getData().put(PropertyKey.ID.toString(), srcName + targetName);
                edge.getData().put(PropertyKey.SOURCE.toString(), srcName);
                edge.getData().put(PropertyKey.TARGET.toString(), targetName);


                edge.getData().put(PropertyKey.CITED.toString(), getCocitations(srcName, targetName));
                graph.getEdges().add(edge);
            }

            for (String nodeName : nodeNames)
            {
                CytoscapeJsNode node = new CytoscapeJsNode();
                node.getData().put(PropertyKey.ID.toString(), nodeName);
                node.getData().put(PropertyKey.CITED.toString(), getTotalCocitations(nodeName));
                graph.getNodes().add(node);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        networkJson = jsonSerializer.deepSerialize(graph);
        return networkJson;
    }

    /**
     * Gets co-citations of the given gene. Uses local cache if accessed in this run.
     *
     * @param gene gene symbol
     * @return co-citations
     */
    private Map<String, Integer> getCocitationMap(String gene)
    {
        if (!cocitationMap.containsKey(gene))
        {
            cocitationMap.put(gene, cocitMan.getCocitations(gene));
        }

        return cocitationMap.get(gene);
    }

    /**
     * Gets the co-citations of two genes.
     *
     * @param gene1 first gene
     * @param gene2 second gene
     * @return co-citations
     */
    private int getCocitations(String gene1, String gene2)
    {
        Map<String, Integer> map = getCocitationMap(gene1);
        if (map != null && map.containsKey(gene2))
        {
            return map.get(gene2);
        } else return 0;
    }

    /**
     * Calculates the total co-citations of a given gene. This value is useful for co-citation count
     * normalizations purposes.
     *
     * @param gene gene symbol
     * @return total co-citations
     */
    private int getTotalCocitations(String gene)
    {
        Map<String, Integer> map = getCocitationMap(gene);
        if (map == null) return 0;

        int cnt = 0;
        for (Integer i : map.values())
        {
            cnt += i;
        }
        return cnt;
    }

}