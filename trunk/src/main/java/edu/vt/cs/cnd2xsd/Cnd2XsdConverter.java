/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.vt.cs.cnd2xsd;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.jcr.Credentials;
import javax.jcr.LoginException;
import javax.jcr.PropertyType;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.nodetype.InvalidNodeTypeDefinitionException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeExistsException;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.PropertyDefinition;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import org.apache.jackrabbit.core.TransientRepository;
import org.w3.generated.FormChoice;
import org.w3.generated.OpenAttrs;
import org.w3.generated.SchemaElement;
//import org.w3.generated.TopLevelElement;
import javax.xml.namespace.QName;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3.generated.Attribute;
import org.w3.generated.ComplexType;
import org.w3.generated.ComplexTypeElement;
import org.w3.generated.ElementElement;
import org.w3.generated.ExplicitGroup;

/**
 *
 * @author adeka
 */
public class Cnd2XsdConverter
{
    private static Logger log = LoggerFactory.getLogger(Cnd2XsdConverter.class);
    /**
     * map of in-built nodetype vs the properties associated with them.
     * Overwrites the default behavior
     */
    private Map<String, String[]> attrMap ;

    private NodeType[] ntypes;


    private static final String MAINCLI = "java -jar cnd2xsd-<version>.jar";

    private String namespace;
    
    private String root;

    private String rootType;
    



    private void loadPropertyMap(String path)
    {
        FileInputStream stream = null;
        try
        {
            stream = new FileInputStream(path);
            attrMap = new HashMap<String, String[]>();
            byte[] buffer = new byte[512];
            StringBuffer stBuffer = new StringBuffer();
            while(stream.available()>0){
                int n = stream.read(buffer, 0, 512);
                stBuffer.append(new String(buffer,0,n));
            }
            String value = stBuffer.substring(0);
            //now lets split the value into multiple lines
            String[] lines = value.split("\n");
            log.debug("Buffer contains :{} lines", lines.length);

            for(String line: lines){
                String[] kvs = line.split("#");
                String key = kvs[0];
                String[] values = null;
                if(kvs.length > 1){
                 values = kvs[1].split(",");
                 log.debug("Key: {} Value:{}",key, kvs[1] );
                }
                attrMap.put(key, values);

            }

        }
        catch (IOException ex)
        {
            log.error("Exception caught:", ex);
        }
        
        finally
        {
            try
            {
                stream.close();
            }
            catch (IOException ex)
            {
                log.error("Exception caught:", ex);
            }
        }

    }

    /**
     * Registers custom NodeType definitions to the RSR determined by the session.
     * @param session the RSR session
     * @param cndFileName path to the CND file
     * @return the array of registered NodeTypes in the RSR
     * @throws RepositoryException
     * @throws IOException
     */
    public static NodeType[] RegisterCustomNodeTypes(Session session, String cndFileName, String prefix)
            throws RepositoryException, IOException
    {
        FileReader reader = null;
        try
        {
             reader = new FileReader(cndFileName);
  
            NodeType[] newNodeTypes = CndImporter.registerNodeTypes(reader, session);
            for (NodeType nt : newNodeTypes)
            {
                log.debug("Registered: " + nt.getName());
            }
            if(newNodeTypes == null || newNodeTypes.length ==0 )
            {
                NodeTypeManager man = session.getWorkspace().getNodeTypeManager();
                NodeTypeIterator nit = man.getAllNodeTypes();
                List<NodeType> nlist = new LinkedList<NodeType>();
                while(nit.hasNext()){
                    NodeType nt = nit.nextNodeType();
                    if(nt.getName().contains(prefix)){
                        log.debug("node type :"+ nt.getName());
                        nlist.add(nt);
                    }
                }
                return nlist.toArray(new NodeType[nlist.size()]);
            }else{
                return newNodeTypes;
            }
        }
        catch (InvalidNodeTypeDefinitionException ex)
        {
            ex.printStackTrace();
        }
        catch (NodeTypeExistsException ex)
        {
            ex.printStackTrace();
        }
        catch (UnsupportedRepositoryOperationException ex)
        {
            ex.printStackTrace();
        }
        catch (ParseException ex)
        {
            ex.printStackTrace();
        }finally{
            if(reader != null){
                reader.close();
            }
        }
        return null;


    }

    /**
     * Usage: Cnd2Xsd [path to source cnd] [path to write the xsd]
     * @param args
     * @throws LoginException
     * @throws RepositoryException
     * @throws IOException
     * @throws JAXBException
     */
    @SuppressWarnings("static-access")
    public static void main(String[] args) throws LoginException, RepositoryException, IOException, JAXBException, org.apache.commons.cli.ParseException
    {

        Session session = null;
        Cnd2XsdConverter converter = new Cnd2XsdConverter();

        try
        {
            Options opt = new Options();

            opt.addOption(OptionBuilder.hasArg(true).isRequired(false).
                    withDescription("Path for the input cnd file").create("fc"));
            opt.addOption(OptionBuilder.hasArg(true).isRequired(false).
                    withDescription("Path for properties map.").create("fp") );
            opt.addOption(OptionBuilder.hasArg(true).isRequired(false).
                    withDescription("Path for generating XML schema.").create("fx") );
            opt.addOption(OptionBuilder.hasArg(false).isRequired(false).
                    withDescription("Prints this list.").create("help"));
            opt.addOption(OptionBuilder.hasArg(true).isRequired(false).
                    withDescription("The namespace for the XSD.").create("ns"));
            opt.addOption(OptionBuilder.hasArg(true).isRequired(false).
                    withDescription("The namespace prefix.").create("nsp"));
            opt.addOption(OptionBuilder.hasArg(true).isRequired(false).
                    withDescription("The root element in the XSD.").create("r"));                   
            opt.addOption(OptionBuilder.hasArg(true).isRequired(false).
                    withDescription("The root element type.").create("rtype"));

            //create the basic parser
            BasicParser parser = new BasicParser();
            CommandLine cl = parser.parse(opt, args);
            HelpFormatter f = new HelpFormatter();
            //check if we have any leftover args
            if (cl.getArgs().length != 0 || args.length == 0) {                
                f.printHelp(MAINCLI, opt);
                return;
            }

            if (cl.hasOption("help")) {
                f.printHelp(MAINCLI, opt);
                return;
            }

            String cndFilePath = cl.getOptionValue("fc");
            String xsdFilePath = cl.getOptionValue("fx");
            String propmapPath = cl.getOptionValue("fp");
            String ns = cl.getOptionValue("ns");
            String nsPrefix = cl.getOptionValue("nsp");
            String rt = cl.getOptionValue("r");
            String rtype = cl.getOptionValue("rtype");

            converter.init(cndFilePath, propmapPath, ns, nsPrefix, rt, rtype );
            FileOutputStream fout = new FileOutputStream(xsdFilePath);
            converter.convert(fout);

        }
        finally
        {
            if (session != null)
            {
                session.save();
                session.logout();
            }
        }
    }

    private  void convert(OutputStream stream)
    {
        OutputStream fout = stream;
        try
        {


            SchemaElement schemaRoot = new SchemaElement();

            schemaRoot.setElementFormDefault(FormChoice.QUALIFIED);
            schemaRoot.setTargetNamespace(this.namespace);
            JAXBContext jc = JAXBContext.newInstance(SchemaElement.class);
            Marshaller m = jc.createMarshaller();
            List<OpenAttrs> rootAttrList = schemaRoot.getIncludesAndImportsAndRedefines();
            ElementElement rootElement = new ElementElement();
            QName qname = new QName(this.namespace, this.rootType);
            rootElement.setType(qname);
            rootElement.setName(this.root);
            rootAttrList.add(rootElement);

            //the first level nodes that are children of rsrRoot are those nodes that
            //do not have any parent nodes in the cnd.
  
            for (NodeType nt : ntypes)
            {

                log.debug("NodeType:" + nt.getName());

                //check if we already have that node - if we have then update it

                QName name = getQualifiedName(nt.getName());

                ComplexTypeElement ctype = (ComplexTypeElement) getComplexType(rootAttrList, name.getLocalPart(),
                        attrMap.containsKey(nt.getName()) ? attrMap.get(nt.getName()) : null);

                for (NodeType pt : nt.getDeclaredSupertypes())
                {
                    log.debug("  DeclaredSuperType:" + pt.getName());
                    //based on the supertypes we will have to make decisions
                    if (attrMap.containsKey(pt.getName()))
                    {
                        //check if we have to create a node
                        String[] attrs = attrMap.get(pt.getName());
                        if (attrs != null)
                        {
                            //get the qualified name
                            QName ename = getQualifiedName(pt.getName());
                            //create a complex type
                            //check if the complex type already there in the rootAttrList
                            ComplexType ctf = findComplexType(rootAttrList, ename.getLocalPart());

                            if (ctf == null)
                            {
                                ctf = new ComplexTypeElement();
                                ctf.setName(ename.getLocalPart());
                                //add the attributes
                                for (String attr : attrs)
                                {
                                    Attribute attribute = new Attribute();
                                    QName type = new QName(Constants.XML_NAMESPACE, Constants.STRING);
                                    attribute.setType(type);
                                    attribute.setName(attr);
                                    ctf.getAttributesAndAttributeGroups().add(attribute);
                                }

                                //add this complex type to the attribute list of the root element
                                rootAttrList.add(ctf);
                            }

                            //create an element of the above complex type and add as element
                            ElementElement element = new ElementElement();
                            element.setName(ename.getLocalPart());
                            element.setType(new QName(this.namespace, ctf.getName()));
                            element.setMinOccurs(BigInteger.ONE);
                            element.setMaxOccurs("1");
                            //element.setType(new QName(ctf.));
                            //now add this element to the top level complex type's sequence
                            ctype.getSequence().getElementsAndGroupsAndAlls().add(element);


                        }
                    }
                    //the supertype is not a pre-define type - we then have to add it as an element
                    else
                    {

                        QName qn = getQualifiedName(pt.getName());
                        ComplexType ctf = getComplexType(rootAttrList, qn.getLocalPart(),
                                attrMap.containsKey(nt.getName()) ? attrMap.get(nt.getName()) : null);

                        //create an element of the above type and add as element
                        ElementElement element = new ElementElement();
                        element.setName(qn.getLocalPart());
                        element.setType(new QName(this.namespace, ctf.getName()));
                        element.setMinOccurs(BigInteger.ONE);
                        element.setMaxOccurs("1");

                        //element.setType(new QName(ctf.));
                        //now add this element to the top level complex type's sequence
                        ctype.getSequence().getElementsAndGroupsAndAlls().add(element);

                    }
                }

                for (NodeDefinition nd : nt.getDeclaredChildNodeDefinitions())
                {
                    log.debug("  Declared ChildNode Definition:" + nd.getName());
                    //check default primary type
                    NodeType defaultNT = nd.getDefaultPrimaryType();
                    if (defaultNT == null)
                    {
                        log.debug("Default Primary Type for the node:" + nd.getName() + " is null");
                        //look for the primary type
                        NodeType[] nts = nd.getRequiredPrimaryTypes();
                        if (ntypes == null)
                        {
                            log.debug("No required primary type for node:" + nd.getName());
                        }
                        else
                        {
                            defaultNT = nts[0];
                            log.debug("Assuming first primary  type:" +
                                    defaultNT.getName() + " for node:" + nd.getName());
                        }

                    }
                    log.debug("  Default Primary Type Name:" + defaultNT.getName());
                    ElementElement element = new ElementElement();
                    if (nd.getName().equals("*"))
                    {
                        QName qn = getQualifiedName(defaultNT.getName());
                        ComplexType ct = getComplexType(rootAttrList, qn.getLocalPart(),
                                attrMap.containsKey(nt.getName()) ? attrMap.get(nt.getName()) : null);

                        //QName ename = getQualifiedName(ct.getName());
                        element.setName(ct.getName());
                        element.setType(new QName(this.namespace, ct.getName()));
                        element.setMinOccurs(nd.isMandatory() ? BigInteger.ONE : BigInteger.ZERO);
                        //add an attribute called nodename so that it can be used to identify the node
                        QName type = new QName(Constants.XML_NAMESPACE, Constants.STRING);
                        Attribute attribute = new Attribute();
                        attribute.setType(type);
                        attribute.setName("nodename");
                        ct.getAttributesAndAttributeGroups().add(attribute);

                        if (nd.allowsSameNameSiblings())
                        {
                            element.setMaxOccurs(Constants.UNBOUNDED);
                        }

                    }
                    else
                    {

                        QName qn = getQualifiedName(defaultNT.getName());
                        ComplexType ct = getComplexType(rootAttrList, qn.getLocalPart(),
                                attrMap.containsKey(nt.getName()) ? attrMap.get(nt.getName()) : null);

                        QName ename = getQualifiedName(nd.getName());
                        element.setName(ename.getLocalPart());
                        element.setType(new QName(this.namespace, ct.getName()));
                        element.setMinOccurs(nd.isMandatory() ? BigInteger.ONE : BigInteger.ZERO);

                        if (nd.allowsSameNameSiblings())
                        {
                            element.setMaxOccurs(Constants.UNBOUNDED);
                        }

                    }
                    ctype.getSequence().getElementsAndGroupsAndAlls().add(element);

                }

                for (PropertyDefinition pDef : nt.getPropertyDefinitions())
                {
                    log.debug("    Attr Name:" + pDef.getName());
                    log.debug("    Req type:" + pDef.getRequiredType());
                    log.debug("    Declaring Node type:" + pDef.getDeclaringNodeType().getName());
                    if (pDef.getDeclaringNodeType().getName().equals(nt.getName()))
                    {

                        QName qn = getQualifiedName(pDef.getName());
                        if (!pDef.isMultiple())
                        {
                            Attribute attr = new Attribute();
                            if (isUnsupportedType(pDef.getRequiredType()))
                            {
                                attr.setType(new QName(Constants.XML_NAMESPACE, Constants.STRING));

                            }
                            else
                            {
                                attr.setType(new QName(Constants.XML_NAMESPACE,
                                        PropertyType.nameFromValue(pDef.getRequiredType()).toLowerCase()));
                            }
                            attr.setName(qn.getLocalPart());
                            //handle default value
                            Value[] defaultValues = pDef.getDefaultValues();
                            if(defaultValues != null && defaultValues.length > 0){
                                attr.setDefault(defaultValues[0].getString());
                            }
                            
                            ctype.getAttributesAndAttributeGroups().add(attr);
                        }
                        else
                        {
                            ComplexType ctf = getComplexType(rootAttrList, qn.getLocalPart(),
                                    attrMap.containsKey(nt.getName()) ? attrMap.get(nt.getName()) : null);
                            if (ctf != null)
                            {
                                ElementElement element = new ElementElement();
                                element.setName(qn.getLocalPart());
                                element.setMinOccurs(BigInteger.ZERO);
                                element.setMaxOccurs(Constants.UNBOUNDED);
                                if (isUnsupportedType(pDef.getRequiredType()))
                                {
                                    element.setType(new QName(Constants.XML_NAMESPACE, Constants.STRING));

                                }
                                else
                                {
                                    element.setType(new QName(Constants.XML_NAMESPACE,
                                            PropertyType.nameFromValue(pDef.getRequiredType()).toLowerCase()));
                                }
                                ctf.getSequence().getElementsAndGroupsAndAlls().add(element);

                            }

                            //now create an element of the above type
                            ElementElement element = new ElementElement();
                            element.setName(qn.getLocalPart());
                            element.setType(new QName(this.namespace, ctf.getName()));
                            ctype.getSequence().getElementsAndGroupsAndAlls().add(element);

                        }

                    }

                }



            }
            //decide what to put under rootNode

            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            //fout = new FileOutputStream(fileName);
            m.marshal(schemaRoot, fout);

        }
        catch (Exception ex)
        {
            log.debug("Exception:" + ex.getMessage());
            ex.printStackTrace();
        }
        finally
        {
            if (fout != null)
            {
                try
                {
                    fout.close();
                }
                catch (IOException ex)
                {
                    log.error("Exception caught: {}", ex.getMessage());
                }
            }
        }


    }

    private static QName getQualifiedName(String name)
    {
        String[] tokens = name.split(":");
        if (tokens.length < 2)
        {
            return null;
        }
        else
        {
            QName qname = new QName(tokens[0], tokens[1]);
            return qname;
        }

    }

    private static ComplexType findComplexType(List<OpenAttrs> rootAttrList, String name)
    {

        ComplexType complexType = null;
        for (Object obj : rootAttrList)
        {
            if (obj instanceof ComplexType)
            {
                ComplexType ct = (ComplexType) obj;
                if (ct.getName().equals(name))
                {
                    complexType = ct;
                    break;
                }
            }
        }
        return complexType;
    }

    private static ComplexType getComplexType(List<OpenAttrs> rootAttrList, String name, String[] attrs)
    {
        ComplexType complexType = findComplexType(rootAttrList, name);

        ComplexTypeElement ctype = (ComplexTypeElement) complexType;
        if (ctype == null)
        {
            //Any node type that we encounter should be a ComplexType in the XSD
            ctype = new ComplexTypeElement();
            ctype.setName(name);
            ExplicitGroup seq = new ExplicitGroup();
            ctype.setSequence(seq);
            rootAttrList.add(ctype);
            if (attrs != null)
            {
                for (String attr : attrs)
                {
                    Attribute attribute = new Attribute();
                    QName type = new QName(Constants.XML_NAMESPACE, Constants.STRING);
                    attribute.setType(type);
                    attribute.setName(attr);
                    ctype.getAttributesAndAttributeGroups().add(attribute);
                }
            }

            log.debug("We have added complextype:" + ctype.getName());
        }
        return ctype;

    }

    private static boolean isUnsupportedType(int requiredType)
    {
        if (requiredType == PropertyType.LONG)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    private void init(String cndFilePath, String propmapPath,
            String namespace, String prefix, String rt, String rtype) throws RepositoryException, IOException
    {
        Session session = null;
        try
        {
            //first register the CND with the repository
            Repository repository = new TransientRepository();
            Credentials c = new SimpleCredentials(Constants.DEFAULT_USERID, Constants.DEFAULT_PASS.toCharArray());
            session = repository.login(c);
            String user = session.getUserID();
            String name = repository.getDescriptor(Repository.REP_NAME_DESC);
            log.debug("Logged in as {} to a repository :{}", user,  name );
            //now lets load a NodeTypeDef to the session
            ntypes = RegisterCustomNodeTypes(session, cndFilePath, prefix);
            session.save();
            this.namespace = namespace;            
            this.root = rt;
            this.rootType = rtype;

            //setup the property map
            this.loadPropertyMap(propmapPath);


        }
        finally{
            session.logout();
        }

    }
}
