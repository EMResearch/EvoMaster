using System;
using System.Collections.Generic;
using System.IO;
using EvoMaster.Instrumentation_Shared;
using EvoMaster.Instrumentation.StaticState;
using Microsoft.Extensions.Logging;
using Mono.Cecil;
using Mono.Cecil.Cil;
using Mono.Cecil.Rocks;

namespace EvoMaster.Instrumentation {
    public class Instrumentator {
        private MethodReference _probe;
        private readonly ILogger _logger;

        public Instrumentator(ILogger<Instrumentator> logger) {
            _logger = logger;
        }

        /// <summary>
        /// This method instruments an assembly file and saves its instrumented version in the specified destination directory
        /// </summary>
        /// <param name="assembly">Name of the file to be instrumented</param>
        /// <param name="destination">Directory where the instrumented file should be copied in</param>
        /// <exception cref="ArgumentNullException"></exception>
        public void Instrument(string assembly, string destination) {
            if (string.IsNullOrEmpty(assembly)) throw new ArgumentNullException(assembly);
            if (string.IsNullOrEmpty(destination)) throw new ArgumentNullException(destination);

            //ReadSymbols is set true to enable getting line info
            var module =
                ModuleDefinition.ReadModule(assembly, new ReaderParameters { ReadSymbols = true });

            //TODO: check file extension

            _probe =
                module.ImportReference(
                    typeof(Instrumentator).GetMethod(name: "CompletedLine",
                        types: new[] { typeof(string), typeof(string), typeof(int) }));

            foreach (var type in module.Types) {
                if (type.Name == "<Module>") continue;

                foreach (var method in type.Methods) {
                    if (!method.HasBody) continue;

                    var ilProcessor = method.Body.GetILProcessor();

                    var mapping = method.DebugInformation.GetSequencePointMapping();

                    var lastReachedLine = 0;
                    var alreadyCompletedLines = new List<int>();
                    method.Body.SimplifyMacros(); //This is to prevent overflow of short branch opcodes

                    for (var i = 0; i < int.MaxValue; i++) {
                        Instruction instruction;
                        try {
                            instruction = method.Body.Instructions[i];
                        }
                        catch (ArgumentOutOfRangeException) {
                            break;
                        }

                        mapping.TryGetValue(instruction, out var sequencePoint);

                        if ((sequencePoint == null || sequencePoint.IsHidden) && instruction.OpCode != OpCodes.Ret)
                            continue;

                        if (sequencePoint != null && sequencePoint.StartLine == lastReachedLine)
                            continue;

                        if (lastReachedLine != 0) {
                            //This is to prevent insertion of completed probe after branch opcode
                            //Checking alreadyCompletedLines is in order to control calling Completed probe in loops two times...
                            //However I'm not sure this will work in all cases, if it didn't work, we can try branchInstruction.Operand.Next
                            if (IsBranchInstruction(instruction.Previous) &&
                                !alreadyCompletedLines.Contains(lastReachedLine)) {
                                i = InsertVisitLineProbe(instruction.Previous, ilProcessor, i, type.Name, method.Name,
                                    lastReachedLine);
                            }
                            else {
                                i = InsertVisitLineProbe(instruction, ilProcessor, i, type.Name, method.Name,
                                    lastReachedLine);

                                //To cover cases when ret has line number
                                if (instruction.OpCode == OpCodes.Ret) {
                                    if (sequencePoint != null) {
                                        i = InsertVisitLineProbe(instruction, ilProcessor, i, type.Name,
                                            method.Name,
                                            sequencePoint.EndLine);
                                    }
                                }
                            }

                            alreadyCompletedLines.Add(lastReachedLine);
                        }

                        if (sequencePoint != null && !sequencePoint.IsHidden)
                            lastReachedLine = sequencePoint.StartLine;
                    }

                    method.Body.OptimizeMacros(); //Change back Br opcodes to Br.s if possible
                }
            }

            if (destination.Length > 1 && destination[^1] == '/') {
                destination = destination.Remove(destination.Length - 1, 1);
            }

            module.Write($"{destination}/InstrumentedSut.dll");
            
            _logger.LogInformation($"Instrumented File Saved Successfully at \"{destination}\"");
        }

        private int InsertVisitLineProbe(Instruction instruction, ILProcessor ilProcessor,
            int byteCodeIndex, string className, string methodName, int line) {
            var classNameInstruction = ilProcessor.Create(OpCodes.Ldstr, className);
            var methodNameInstruction = ilProcessor.Create(OpCodes.Ldstr, methodName);
            var lineNumberInstruction = ilProcessor.Create(OpCodes.Ldc_I4, line);
            var visitedInstruction = ilProcessor.Create(OpCodes.Call, _probe);

            ilProcessor.InsertBefore(instruction, classNameInstruction);
            byteCodeIndex++;
            ilProcessor.InsertBefore(instruction, methodNameInstruction);
            byteCodeIndex++;
            ilProcessor.InsertBefore(instruction, lineNumberInstruction);
            byteCodeIndex++;
            ilProcessor.InsertBefore(instruction, visitedInstruction);
            byteCodeIndex++;

            return byteCodeIndex;
        }

        private static bool IsBranchInstruction(Instruction instruction) =>
            instruction.OpCode.ToString().ToLower()[0].Equals('b') && instruction.OpCode != OpCodes.Break &&
            instruction.OpCode != OpCodes.Box;

        //This method is called by the probe inserted after each covered line in the instrumented SUT
        public static void CompletedLine(string className, string methodName, int lineNo) {
            ObjectiveRecorder.RegisterTarget(ObjectiveNaming.LineObjectiveName(className, lineNo));

            //TODO: description
            ExecutionTracer.ExecutedLine(className, methodName, "desc", lineNo);
        }
    }
}