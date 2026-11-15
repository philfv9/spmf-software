package ca.pfv.spmf.tools.dataset_converter;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
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