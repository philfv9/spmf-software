package ca.pfv.spmf.algorithms.frequentpatterns.mehuim;

public class SearchNode {
    Transaction t;
    int transactionUtility;
    int prefixUtility;

    SearchNode()
    {
        ;
    }

    SearchNode(Transaction t, int transactionUtility, int prefixUtility)
    {
        this.t = t;
        this.transactionUtility = transactionUtility;
        this.prefixUtility = prefixUtility;
    }
}