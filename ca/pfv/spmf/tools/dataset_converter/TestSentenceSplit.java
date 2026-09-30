package ca.pfv.spmf.tools.dataset_converter;
import java.util.*;

public class TestSentenceSplit {
    public static void main(String[] args) {
        String text = "All ye mast-headers have before now heard me give orders about a white whale. Look ye! d'ye see this Spanish ounce of gold?”—holding up a broad bright coin to the sun—“it is a sixteen dollar piece, men.";

        text = text.replaceAll("([\u2013\u2014\u201C\u201D\u2018\u2019\"])", " $1 ");

        String[] sentences = text.split(
            "(?<=[.!?\u3002\uFF01\uFF1F])[\"'\u201C\u201D\u2018\u2019\\)\\]]*\\s+");

        for (String s : sentences) {
            System.out.println("SENTENCE: [" + s.trim() + "]");
        }
    }
}