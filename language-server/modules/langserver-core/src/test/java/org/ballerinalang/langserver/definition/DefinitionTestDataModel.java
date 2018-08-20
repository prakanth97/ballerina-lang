/*
*  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.ballerinalang.langserver.definition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Class to hold the data for the definition tests.
 */
public class DefinitionTestDataModel {

    private String expectedFileName;

    private String definitionFileURI;

    private String ballerinaFilePath;

    private String ballerinaFileContent;

    public DefinitionTestDataModel(String expectedFileName, Path definitionPath, Path filePath) throws IOException {
        byte[] encodedContent = Files.readAllBytes(filePath);
        this.expectedFileName = expectedFileName;
        this.definitionFileURI = definitionPath.toUri().toString();
        this.ballerinaFilePath = filePath.toString();
        this.ballerinaFileContent = new String(encodedContent);
    }

    public String getExpectedFileName() {
        return expectedFileName;
    }

    public String getDefinitionFileURI() {
        return definitionFileURI;
    }

    public String getBallerinaFilePath() {
        return ballerinaFilePath;
    }

    public String getBallerinaFileContent() {
        return ballerinaFileContent;
    }
}
