/*
 * This file is part of choco-parsers, http://choco-solver.org/
 *
 * Copyright (c) 2024, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.parser.xcsp;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.search.limits.NodeCounter;

import java.util.Arrays;

public class XCSPMemoryUsage {

    public static void main(String[] args) throws Exception {
        //System.out.println("command is " + Arrays.toString(args));
        try {
            XCSP xscp = new XCSP();
            if (xscp.setUp(args)) {
                xscp.createSolver();
                xscp.buildModel();
                xscp.configureSearch();
                Model model = xscp.getModel();
                model.getSolver().addStopCriterion(new NodeCounter(model, 1));
                xscp.solve();
            }
        } catch (Exception e) {
            System.out.println("Error with input \"" + Arrays.toString(args).replace("[", "").replace("]", "").replace(",", "") + "\"");
            System.exit(1);
        }
    }

}
