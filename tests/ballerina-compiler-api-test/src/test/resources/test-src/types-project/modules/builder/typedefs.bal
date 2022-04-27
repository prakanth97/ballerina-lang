// Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

type XmlEle xml:Element;

type XmlPi xml:ProcessingInstruction;

type XmlCmnt xml:Comment;

type XmlTxt xml:Text;

type MyInt int;

type XmlUnionA xml:Element|xml:ProcessingInstruction|xml:Text;

type XmlUnionB XmlEle|XmlTxt|XmlCmnt;

type MixXmlA XmlUnionA|XmlUnionB;

type MixXmlB XmlPi|MixXmlC;

type MixXmlC XmlUnionA|XmlTxt|MixXmlA;

type NewEle XmlEle;

type EleTxtCmnt XmlCmnt|xml:Text|NewEle;
