package server;

public class SimilarTemplate implements Comparable<SimilarTemplate>{
    
	private String templateName;
	private int similarity;
	 
	
	public SimilarTemplate(String templateName, int similarity) {
		super();
		this.templateName = templateName;
		this.similarity = similarity;
	}
    

	@Override
	public int compareTo(SimilarTemplate similarityT) {
		return Integer.compare(this.similarity, similarityT.similarity);
	}


	public String getTemplateName() {
		return templateName;
	}


	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}


	public int getSimilarity() {
		return similarity;
	}


	public void setSimilarity(int similarity) {
		this.similarity = similarity;
	}

}
