package ca.pfv.spmf.gui.visual_pattern_viewer;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * The main types of patterns supported by the Visual Pattern Viewer
 * @author Philippe Fournier-viger
 */
public enum PatternType{
    ASSOCIATION_RULES,
    STRING_SEQUENTIAL_PATTERNS,
    STRING_SEQUENTIAL_PATTERNS_NO_GAP,
    SEQUENTIAL_PATTERNS,
    ITEMSETS,
    UNCERTAIN_ITEMSETS;
}