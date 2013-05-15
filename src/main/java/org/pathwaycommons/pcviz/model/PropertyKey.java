package org.pathwaycommons.pcviz.model;

/**
 * @author Ozgun Babur
 */
public enum PropertyKey
{
	ID,
	SOURCE,
	TARGET,
	CITED;

	@Override
	public String toString()
	{
		return super.toString().toLowerCase();
	}
}