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

import java.util.Arrays;

/**
 * Created by cprudhom on 01/09/15.
 * Project: choco-parsers.
 */
public class ChocoXCSP {


    /*
    instances and run time
    Hsp-10405 : <1s
    HCPizza-10-10-2-6-00: 5s
    to limit time: use "-limit 00h00m10s"
     */


    public static void main(String[] args) throws Exception {
        //System.out.println("command is " + Arrays.toString(args));
        try {
            XCSP xscp = new XCSP();
            if (xscp.setUp(args)) {
                xscp.createSolver();
                xscp.buildModel();
                xscp.configureSearch();
                xscp.solve();
            }
        } catch (Exception e) {
            System.out.println("Error with input \"" + Arrays.toString(args).replace("[", "").replace("]", "").replace(",", "") + "\"");
            System.exit(1);
        }
    }
}
