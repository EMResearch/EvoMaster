﻿using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using EvoMaster.Client.Util.Extensions;

namespace EvoMaster.Instrumentation_Shared {
    public class ObjectiveNaming {
        /**
     * Prefix identifier for class coverage objectives.
     * A class is "covered" if at least one of its lines is executed.
     */
        public static readonly string CLASS = "Class";

        /**
     * Prefix identifier for line coverage objectives
     */
        public static readonly string LINE = "Line";

        /**
     * Prefix identifier for branch coverage objectives
     */
        public static readonly string BRANCH = "Branch";

        /**
     * Tag used in a branch id to specify it is for the "true"/then branch
     */
        public static readonly string TRUE_BRANCH = "_trueBranch";

        /**
     * Tag used in a branch id to specify it is for the "false"/else branch
     */
        public static readonly string FALSE_BRANCH = "_falseBranch";

        /**
     * Prefix identifier for MethodReplacement objectives, where we want
     * to cover both possible outcomes, eg true and false
     */
        public static readonly string METHOD_REPLACEMENT = "MethodReplacement";


        /**
     * Prefix identifier for objectives related to calling methods without exceptions
     */
        public static readonly string SUCCESS_CALL = "Success_Call";

        /**
     * Numeric comparison for non-ints, ie long, double and float
     */
        public static readonly string NUMERIC_COMPARISON = "NumericComparison";

        /*
            WARNING: originally where interning all strings, to save memory.
            but that looks like it was having quite a performance hit on LanguageTool.
    
            For the most used methods, added some memoization, as those methods look like
            among the most expensive/used during performance profiling.
    
            One problem though is due to multi-params for indexing... we could use unique global
            ids already at instrumentation time (to force a single lookup, instead of a chain
            of maps), but that would require a major refactoring.
         */

        //TODO: capacity 10_000
        private static readonly IDictionary<string, string> CacheClass = new ConcurrentDictionary<string, string>();

        public static string ClassObjectiveName(string className) {
            return CacheClass.ComputeIfAbsent(className, c => CLASS + "_" + ClassName.Get(c).GetFullNameWithDots());
            //string name = CLASS + "_" + ClassName.get(className).getFullNameWithDots();
            //return name;//.intern();
        }

        public static string NumericComparisonObjectiveName(string id, int res) {
            var name = NUMERIC_COMPARISON + "_" + id + "_" + (res == 0 ? "EQ" : (res < 0 ? "LT" : "GT"));
            return name; //.intern();
        }

        //TODO: capacity 10_000
        private static readonly IDictionary<string, IDictionary<int, string>> LineCache =
            new ConcurrentDictionary<string, IDictionary<int, string>>();

        public static string LineObjectiveName(string className, int line) {
            var map =
                LineCache.ComputeIfAbsent(className,
                    c => new ConcurrentDictionary<int, string>()); //TODO: capacity 1000
            return map.ComputeIfAbsent(line,
                l => LINE + "_at_" + ClassName.Get(className).GetFullNameWithDots() + "_" + PadNumber(line));

//        string name = LINE + "_at_" + ClassName.get(className).getFullNameWithDots() + "_" + padNumber(line);
//        return name;//.intern();
        }

        //TODO: capacity 10_000
        private static readonly IDictionary<string, IDictionary<int, IDictionary<int, string>>> CacheSuccessCall =
            new ConcurrentDictionary<string, IDictionary<int, IDictionary<int, string>>>();

        public static string SuccessCallObjectiveName(string className, int line, int index) {
            var m0 =
                CacheSuccessCall.ComputeIfAbsent(className,
                    c => new ConcurrentDictionary<int, IDictionary<int, string>>()); //TODO: capacity 10_000
            var
                m1 = m0.ComputeIfAbsent(line, l => new ConcurrentDictionary<int, string>()); //TODO: capacity 10
            return m1.ComputeIfAbsent(index, i =>
                SUCCESS_CALL + "_at_" + ClassName.Get(className).GetFullNameWithDots() +
                "_" + PadNumber(line) + "_" + index);
        }

        public static string MethodReplacementObjectiveNameTemplate(string className, int line, int index) {
            var name = METHOD_REPLACEMENT + "_at_" + ClassName.Get(className).GetFullNameWithDots() +
                       "_" + PadNumber(line) + "_" + index;
            return name; //.intern();
        }

        public static string MethodReplacementObjectiveName(string template, bool result, ReplacementType type) {
            if (template == null || !template.StartsWith(METHOD_REPLACEMENT)) {
                throw new ArgumentException("Invalid template for bool method replacement: " + template);
            }

            var name = template + "_" + type + "_" + result;
            return name; //.intern();
        }


        private static readonly IDictionary<string, IDictionary<int, IDictionary<int, IDictionary<bool, string>>>>
            BranchCache = new
                ConcurrentDictionary<string,
                    IDictionary<int, IDictionary<int, IDictionary<bool, string>>>>(); //TODO: capacity 10_000

        public static string BranchObjectiveName(string className, int line, int branchId, bool thenBranch) {
            var m0 =
                BranchCache.ComputeIfAbsent(className,
                    k => new ConcurrentDictionary<int,
                        IDictionary<int, IDictionary<bool, string>>>()); //TODO: capacity 10_000
            var m1 = m0.ComputeIfAbsent(line,
                k => new ConcurrentDictionary<int, IDictionary<bool, string>>()); //TODO: capacity 10
            var
                m2 = m1.ComputeIfAbsent(branchId, k => new ConcurrentDictionary<bool, string>()); //TODO: capacity 2

            return m2.ComputeIfAbsent(thenBranch, k => {
                var name = BRANCH + "_at_" +
                           ClassName.Get(className).GetFullNameWithDots()
                           + "_at_line_" + PadNumber(line) + "_position_" + branchId;
                if (thenBranch) {
                    name += TRUE_BRANCH;
                }
                else {
                    name += FALSE_BRANCH;
                }

                return name;
            });
        }

        private static string PadNumber(int val) {
            if (val < 0) {
                throw new ArgumentException("Negative number to pad");
            }

            if (val < 10) {
                return "0000" + val;
            }

            if (val < 100) {
                return "000" + val;
            }

            if (val < 1_000) {
                return "00" + val;
            }

            if (val < 10_000) {
                return "0" + val;
            }
            else {
                return "" + val;
            }
        }
    }
}