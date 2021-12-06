using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using EvoMaster.Instrumentation_Shared;

namespace EvoMaster.Instrumentation.StaticState {
    public static class ExecutionTracer {
        /*
         * Key -> the unique descriptive id of the coverage objective
         *
         * java version: private static final Map<String, TargetInfo> objectiveCoverage = new ConcurrentHashMap<>(65536);
         * Note that there is no constructor to only init capacity.
         * https://docs.microsoft.com/en-us/dotnet/api/system.collections.concurrent.concurrentdictionary-2?view=netcore-3.1
         * TODO need a further check whether the current concurrencylevel setting is property later.
         * */
        private static readonly IDictionary<string, TargetInfo> ObjectiveCoverage =
            new ConcurrentDictionary<string, TargetInfo>(Environment.ProcessorCount, 65536);


        /*
        A test case can be composed by 1 or more actions, eg HTTP calls.
        When we get the best distance for a testing target, we might
        also want to know which action in the test led to it.
        */
        private static int _actionIndex = 0;

        /*
        A set of possible values used in the tests, needed for some kinds
        of taint analyses
        */
        private static ISet<string> _inputVariables = new HashSet<string>();

        /*
        Besides code coverage, there might be other events that we want to
        keep track during test execution.
        We keep track of it separately for each action
        */
        private static readonly IList<AdditionalInfo> AdditionalInfoList = new List<AdditionalInfo>();

        /*
        Keep track of expensive operations. Might want to skip doing them if too many.
        This should be re-set for each action
        */
        private static int _expensiveOperation = 0;

        private static readonly object _lock = new object();


        /*
         One problem is that, once a test case is evaluated, some background tests might still be running.
         We want to kill them to avoid issue (eg, when evaluating new tests while previous threads are still running).
         */
        private static volatile bool _killSwitch = false;

        static ExecutionTracer() {
            Reset();
        }


        public static void Reset() {
            lock (_lock) {
                ObjectiveCoverage.Clear();
                _actionIndex = 0;
                AdditionalInfoList.Clear();
                AdditionalInfoList.Add(new AdditionalInfo());
                _inputVariables = new HashSet<string>();
                _killSwitch = false;
                _expensiveOperation = 0;
            }
        }

        public static bool IsKillSwitch() {
            return _killSwitch;
        }

        public static void SetAction(Action action) {
            lock (_lock) {
                SetKillSwitch(false);
                _expensiveOperation = 0;
                if (action.GetIndex() != _actionIndex) {
                    _actionIndex = action.GetIndex();
                    AdditionalInfoList.Add(new AdditionalInfo());
                }

                if (action.GetInputVariables() != null && action.GetInputVariables().Count != 0) {
                    _inputVariables = action.GetInputVariables();
                }
            }
        }

        public static void SetKillSwitch(bool killSwitch) {
            ExecutionTracer._killSwitch = killSwitch;
        }

        public static IList<AdditionalInfo> ExposeAdditionalInfoList() {
            return AdditionalInfoList;
        }


        ///<summary>Report on the fact that a given line has been executed.</summary>
        public static void ExecutedLine(string className, string methodName, string descriptor, int line) {
            //This is done to prevent the SUT keep on executing code after a test case is evaluated
            if (IsKillSwitch()) {
                //TODO
                // var initClass = Arrays.stream(Thread.CurrentThread..getStackTrace())
                //     .anyMatch(e -> e.getMethodName().equals("<clinit>"));

                /*
                    must NOT stop the initialization of a class, otherwise the SUT will be left in an
                    inconsistent state in the following calls
                 */

                // if (!initClass)
                // {
                //     throw new KillSwitchException();
                // }
            }

            //TODO
            //for targets to cover
            var lineId = ObjectiveNaming.LineObjectiveName(className, line);
            var classId = ObjectiveNaming.ClassObjectiveName(className);
            UpdateObjective(lineId, 1d);
            UpdateObjective(classId, 1d);

            //to calculate last executed line
            var lastLine = className + "_" + line + "_" + methodName;
            var lastMethod = className + "_" + methodName + "_" + descriptor;
            MarkLastExecutedStatement(lastLine, lastMethod);
        }

        public static void MarkLastExecutedStatement(string lastLine, string lastMethod) {
            //TODO
            //GetCurrentAdditionalInfo().PushLastExecutedStatement(lastLine, lastMethod);
        }

        ///<returns>the number of objectives that have been encountered during the test execution</returns>
        public static int GetNumberOfObjectives() => ObjectiveCoverage.Count;

        public static int GetNumberOfObjectives(string prefix) =>
            ObjectiveCoverage.Count(e => prefix == null || e.Key.StartsWith(prefix));
        
        public static IDictionary<string, TargetInfo> GetInternalReferenceToObjectiveCoverage() => ObjectiveCoverage;

        private static void UpdateObjective(string id, double value) {
            if (value < 0d || value > 1d) {
                throw new ArgumentException("Invalid value " + value + " out of range [0,1]");
            }

            //In the same execution, a target could be reached several times, so we should keep track of the best value found so far
            lock (_lock) {
                if (ObjectiveCoverage.ContainsKey(id)) {
                    var previous = ObjectiveCoverage[id].Value;
                    if (value > previous) {
                        ObjectiveCoverage.Add(id, new TargetInfo(null, id, value, _actionIndex));
                    }
                }
                else {
                    ObjectiveCoverage.Add(id, new TargetInfo(null, id, value, _actionIndex));
                }
            }

            ObjectiveRecorder.Update(id, value);
        }

        private static AdditionalInfo GetCurrentAdditionalInfo() {
            lock (_lock) {
                return AdditionalInfoList[_actionIndex];
            }
        }
    }
}