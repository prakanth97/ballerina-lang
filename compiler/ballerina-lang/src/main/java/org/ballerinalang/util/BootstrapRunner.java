/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.util;

import org.ballerinalang.compiler.BLangCompilerException;
import org.ballerinalang.model.elements.PackageID;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.programfile.PackageFileWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * Creates jars in file system using bootstrap pack and create class loader hierarchy for them.
 */
public class BootstrapRunner {

    private static final PrintStream out = System.out;
    private static final PrintStream err = System.err;
    private static String CLASSPATH = "CLASSPATH";

    public static void loadTargetAndGenerateJarBinary(String entryBir, String jarOutputPath, boolean dumpBir,
                                                      HashSet<Path> moduleDependencySet, String... birCachePaths) {
        //Load all Jars from module dependency set.
        List<String> jarFilePaths = new ArrayList<>(moduleDependencySet.size());
        moduleDependencySet.forEach(path -> jarFilePaths.add(path.toString()));
        generateJarBinaryInProc(entryBir, jarOutputPath, dumpBir, jarFilePaths, birCachePaths);
    }

    private static void generateJarBinaryInProc(String entryBir, String jarOutputPath, boolean dumpBir,
                                                List<String> jarFilePaths, String... birCachePaths) {
        try {
            List<String> commands = new ArrayList<>();
            commands.add("java");
            setSystemProperty(commands, "ballerina.bstring");
            commands.add("ballerina.compiler_backend_jvm.___init");
            commands.addAll(createArgsForCompilerBackend(entryBir, jarOutputPath, dumpBir, true, birCachePaths,
                    jarFilePaths));

            // pass the classpath for the sub-process
            ProcessBuilder pb = new ProcessBuilder(commands);
            Map<String, String> env = pb.environment();
            env.put(CLASSPATH, System.getProperty("java.class.path"));

            Process process = pb.start();
            getConsoleOutput(process.getInputStream(), out);
            String consoleError = getConsoleOutput(process.getErrorStream(), err);
            boolean processEnded = process.waitFor(120, TimeUnit.SECONDS);
            if (!processEnded) {
                throw new BLangCompilerException("failed to generate jar file within 120s.");
            }
            if (process.exitValue() != 0) {
                throw new BLangCompilerException(consoleError);
            }
        } catch (InterruptedException | IOException e) {
            throw new BLangCompilerException("failed running jvm code gen phase.", e);
        }
    }

    private static void setSystemProperty(List<String> commands, String propertName) {
        String value = System.getProperty(propertName);
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        commands.add("-D" + propertName + "=" + System.getProperty(propertName));
    }

    private static String getConsoleOutput(InputStream inputStream, PrintStream printStream) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringJoiner sj = new StringJoiner(System.getProperty("line.separator"));
        reader.lines().iterator().forEachRemaining(line -> {
            printStream.println(line);
            sj.add(line);
        });
        return sj.toString();
    }

    private static List<String> createArgsForCompilerBackend(String entryBir, String jarOutputPath, boolean dumpBir,
                                                             boolean useSystemClassLoader, String[] birCachePaths,
                                                             List<String> jarFilePaths) {
        List<String> commands = new ArrayList<>();
        commands.add(entryBir);
        commands.add(getMapPath());
        commands.add(jarOutputPath);
        commands.add(dumpBir ? "true" : "false"); // dump bir
        commands.add(useSystemClassLoader ? "true" : "false"); // useSystemClassLoader
        commands.add(String.valueOf(birCachePaths.length));
        commands.addAll(Arrays.asList(birCachePaths));
        commands.addAll(jarFilePaths);
        return commands;
    }


    private static String getMapPath() {
        String ballerinaNativeMap = System.getenv("BALLERINA_NATIVE_MAP");
        return ballerinaNativeMap == null ? " " : ballerinaNativeMap;
    }

    private static void writeNonEntryPkgs(List<BPackageSymbol> imports, Path birCache, Path importsBirCache,
                                          Path jarTargetDir, boolean dumpBir)
            throws IOException {
        for (BPackageSymbol pkg : imports) {
            PackageID id = pkg.pkgID;
            //Todo: ballerinax check shouldn't be here. This should be fixed by having a proper package hierarchy.
            //Todo: Remove ballerinax check after fixing it by the packerina team
            if (!"ballerina".equals(id.orgName.value) && !"ballerinax".equals(id.orgName.value)) {
                writeNonEntryPkgs(pkg.imports, birCache, importsBirCache, jarTargetDir, dumpBir);
                Path pkgBir = getModuleBir(pkg, importsBirCache);
                String jarOutputPath = jarTargetDir.resolve(id.name.value + ".jar").toString();
                generateJarBinaryInProc(pkgBir.toString(), jarOutputPath, dumpBir, Collections.emptyList(),
                        birCache.toString(), importsBirCache.toString());
            }
        }
    }

    private static Path getModuleBir(BPackageSymbol pkg, Path importsBirCache) throws IOException {
        PackageID id = pkg.pkgID;
        byte[] bytes = PackageFileWriter.writePackage(pkg.birPackageFile);
        Path pkgBirDir = importsBirCache.resolve(id.orgName.value)
                .resolve(id.name.value)
                .resolve(id.version.value.isEmpty() ? "0.0.0" : id.version.value);
        Files.createDirectories(pkgBirDir);
        Path pkgBir = pkgBirDir.resolve(id.name.value + ".bir");
        Files.write(pkgBir, bytes);
        return pkgBir;
    }

    public static URLClassLoader createClassLoaders(BLangPackage bLangPackage,
                                                 Path systemBirCache,
                                                 Path buildRoot,
                                                 Optional<Path> jarTargetRoot,
                                                 boolean dumpBir) throws IOException {
        byte[] bytes = PackageFileWriter.writePackage(bLangPackage.symbol.birPackageFile);
        String fileName = calcFileNameForJar(bLangPackage);
        Files.createDirectories(buildRoot);
        Path intermediates = Files.createTempDirectory(buildRoot, fileName + "-");
        Path entryBir = intermediates.resolve(fileName + ".bir");
        Path jarTarget = jarTargetRoot.orElse(intermediates).resolve(fileName + ".jar");
        Files.write(entryBir, bytes);

        Path importsBirCache = intermediates.resolve("imports").resolve("bir-cache");
        Path importsTarget = importsBirCache.getParent().resolve("generated-bir-jar");
        Files.createDirectories(importsTarget);

        writeNonEntryPkgs(bLangPackage.symbol.imports, systemBirCache, importsBirCache, importsTarget, dumpBir);
        generateJarBinaryInProc(entryBir.toString(), jarTarget.toString(), dumpBir, Collections.emptyList(),
                systemBirCache.toString(), importsBirCache.toString());

        if (!Files.exists(jarTarget)) {
            throw new RuntimeException("Compiled binary jar is not found: " + jarTarget);
        }
        int index = 0;
        URL[] urls;
        File importsCache = importsTarget.toFile();
        if (importsCache.isDirectory()) {
            String[] jarFIles = importsCache.list();
            urls = new URL[jarFIles.length + 1];
            for (String file : jarFIles) {
                urls[index++] = Paths.get(importsCache.getPath(), file).toUri().toURL();
            }
        } else {
            urls = new URL[1];
        }
        urls[index] = jarTarget.toFile().toURI().toURL();
        return new URLClassLoader(urls);
    }

    private static String calcFileNameForJar(BLangPackage bLangPackage) {
        PackageID pkgID = bLangPackage.pos.src.pkgID;
        Name sourceFileName = pkgID.sourceFileName;
        if (sourceFileName != null) {
            return sourceFileName.value.replaceAll("\\.bal$", "");
        }
        return pkgID.name.value;
    }
}

