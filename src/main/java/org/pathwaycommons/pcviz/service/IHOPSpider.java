package org.pathwaycommons.pcviz.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class parses co-citation information in iHOP database.
 *
 * @author Ozgun Babur
 */
@Service
public class IHOPSpider
{
	private static final Log log = LogFactory.getLog(IHOPSpider.class);
	public static final String DEFAULT_URL = "http://www.ihop-net.org/UniPub/iHOP/";

	@Value("${ihop.url:http://www.ihop-net.org/UniPub/iHOP/}")
    private String iHopURL;

    public IHOPSpider() {
    	iHopURL = DEFAULT_URL; //for using w/o a Spring context
    }

	public String getiHopURL() {
		return iHopURL;
	}

	public void setiHopURL(String iHopURL) {
		this.iHopURL = iHopURL;
	}

    /**
	 * Gets the co-citation data from the iHOP server.
	 * @param symbol symbol of the gene of interest
	 * @return map from co-cited to counts
	 */
	public Map<String, Integer> parseCocitations(String symbol)
	{
		// Find internal ID
		BufferedReader reader = getReader(getGeneSearchURL(symbol));
		try {
			String ID = null;

			if(reader != null) {
				ID = getInternalID(reader, symbol);
				reader.close();
			}

			if (ID == null) {
				log.debug("Cannot find internal ID of " + symbol);
				return null;
			}

			reader = getReader(getGenePageURL(ID));
            Map<String, Integer> map = parseCocitations(reader);
            reader.close();

            return map;
		} catch (Exception e)
		{
			log.warn("Cannot parse co-citations for " + symbol, e);
            return null;
		}
	}

	/**
	 * Prepares the URL for searching a gene.
	 * @param symbol gene symbol of the gene
	 * @return url
	 */
	private String getGeneSearchURL(String symbol)
	{
		return  iHopURL + "?field=synonym&ncbi_tax_id=9606&search=" + symbol;
	}

	/**
	 * Prepares the URL for the main gene page with internal ID.
	 * @param internalID internal ID
	 * @return url
	 */
	private String getGenePageURL(String internalID)
	{
		return iHopURL +"/gs/" + internalID + ".html?list=1&page=1";
	}

	/**
	 * Gets a BufferedReader for the given URL.
	 * @param url url of the resource
	 * @return buffered reader
	 */
	private static BufferedReader getReader(String url)
	{
		try
		{
			URL u = new URL(url);
			URLConnection con = u.openConnection();
			InputStream is = con.getInputStream();
			return new BufferedReader(new InputStreamReader(is));
		}
		catch (IOException e)
		{
			log.error(e.toString());
			return null;
		}
	}

	/**
	 * Extracts the internal ID of the molecule.
	 * @param reader reader of the resource
	 * @return internal ID
	 * @throws IOException
	 */
	private String getInternalID(BufferedReader reader, String symbol) throws IOException
	{
		if(reader == null) {
			log.info("Cannot find internal ID of " + symbol);
			return null;
		}

		List<String> ids = new ArrayList<String>();

		for(String line = reader.readLine(); line != null; line = reader.readLine())
		{
			if (line.startsWith("<TD nowrap=\"1\""))
			{
				int index = line.indexOf("doaction(null, ");
				if (index >= 0)
				{
					ids.add(line.substring(index + 15, line.lastIndexOf(", 1")));
				}
			}

			if (line.equals("<B>" + symbol + "</B>"))
			{
				line = reader.readLine();

				if (!line.equals("</SYMBOL>")) continue;

				line = reader.readLine();

				int index = line.indexOf("doaction(null, ");

				if (index >= 0)
				{
					return line.substring(index + 15, line.lastIndexOf(", 1"));
				}
			}
		}

		// check the encountered internal IDs to see if they map to given symbol
		for (String id : ids)
		{
			String sym = getSymbolOfID(id);

			if (sym != null && sym.equals(symbol))
			{
				return id;
			}
		}

		// Cannot find
		log.debug("Cannot find internal ID of " + symbol);
		return null;
	}

	/**
	 * Parses the co-citation counts for the given gene page.
	 * @param reader reader for the content
	 * @return map from the co-cited to their counts
	 */
	private Map<String, Integer> parseCocitations(BufferedReader reader) throws IOException
	{
		Map<String, Integer> map = new HashMap<String, Integer>();

		for(String line = reader.readLine(); line != null; line = reader.readLine())
		{
			if (line.startsWith("           hstore(new Array(\"type\", \"GENE\""))
			{
				String symbol = line.substring(line.indexOf("symbol\", \"") + 10, line.indexOf("\", \"name"));
				String count = line.substring(line.lastIndexOf(" \"") + 2, line.lastIndexOf("\""));

				map.put(symbol, Integer.parseInt(count));
			}
		}
		return map;
	}

	/**
	 * Extracts the gene symbol of an internal ID.
	 * @param ID internal ID
	 * @return gene symbol
	 */
	private String getSymbolOfID(String ID)
	{
		String url = getGenePageURL(ID);
		BufferedReader reader = getReader(url);
		try
		{
            String symbol = parseSymbol(reader);
            reader.close();
            return symbol;
		}
		catch (IOException e)
		{
			log.error("Error while extracting gene symbol.", e);
			return null;
		}
	}

	/**
	 * Gets the gene symbol in the title.
	 * @param reader reader for the content
	 * @return gene symbol
	 */
	private String parseSymbol(BufferedReader reader) throws IOException
	{
		for(String line = reader.readLine(); line != null; line = reader.readLine())
		{
			if (line.startsWith("<title>"))
			{
				int start = line.indexOf("[");
				int end = line.indexOf("]");

				if (start > 0 && end > start)
				{
					return line.substring(start + 1, end).trim();
				}
			}
		}
		return null;
	}
}
