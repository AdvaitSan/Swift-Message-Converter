package com.converter;

import com.prowidesoftware.swift.model.mt.mt5xx.MT564;
import com.prowidesoftware.swift.model.field.*;
import com.prowidesoftware.swift.model.mx.MxSeev03100112;
import com.prowidesoftware.swift.model.mx.dic.*;

import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;

public class CorpActnNtfctnConverter {

    private static final Logger logger = Logger.getLogger(CorpActnNtfctnConverter.class.getName());

    public ConversionResult convert(String mt564Raw) throws MT564MissingFieldException {
        ConversionResult result = new ConversionResult();
        logger.info("Starting conversion of MT564");

        MT564 mt564 = MT564.parse(mt564Raw);
        if (mt564 == null || mt564.getSwiftMessage() == null) {
            throw new MT564MissingFieldException("Failed to parse MT564 message.");
        }
        
        // Pre-flight validation
        com.prowidesoftware.swift.model.SwiftBlock4 b4 = mt564.getSwiftMessage().getBlock4();
        if (b4 == null || b4.isEmpty() || !mt564Raw.contains("-}")) {
            throw new MT564MissingFieldException("Block 4 is incomplete or missing closing delimiter");
        }
        
        MT564.SequenceA seqA = mt564.getSequenceA();
        if (seqA == null) {
            throw new MT564MissingFieldException("Sequence A is mandatory");
        }
        
        boolean hasCorp = false;
        for (com.prowidesoftware.swift.model.Tag tag : seqA.getTagsByName("20C")) {
            Field20C f = new Field20C(tag.getValue());
            if ("CORP".equals(f.getQualifier()) && f.getReference() != null && !f.getReference().trim().isEmpty()) {
                hasCorp = true;
                break;
            }
        }
        if (!hasCorp) {
            throw new MT564MissingFieldException("CORP reference is mandatory in Sequence A");
        }
        
        if (seqA.getTagsByName("23G").length == 0) {
            throw new MT564MissingFieldException("Function of Message (23G) is mandatory in Sequence A");
        }
        
        MT564.SequenceB seqB = mt564.getSequenceB();
        if (seqB == null || seqB.getTagsByName("35B").length == 0) {
            throw new MT564MissingFieldException("ISIN (35B) is mandatory in Sequence B");
        }
        
        if (b4.getTagsByName("98A").length == 0) {
            throw new MT564MissingFieldException("At least one Date (98A) is mandatory");
        }

        MxSeev03100112 mx = new MxSeev03100112();
        CorporateActionNotificationV12 ntfctn = new CorporateActionNotificationV12();
        mx.setCorpActnNtfctn(ntfctn);

        CorporateActionGeneralInformation165 corpActnGnlInf = new CorporateActionGeneralInformation165();
        ntfctn.setCorpActnGnlInf(corpActnGnlInf);
        CorporateActionNotification5 ntfctnGnlInf = new CorporateActionNotification5();
        ntfctn.setNtfctnGnlInf(ntfctnGnlInf);

        // Sequence A - CORP reference and 23G Function
        boolean foundCorp = false;
        boolean isRepl = false;
        boolean isEntl = false;
        
        for (com.prowidesoftware.swift.model.Tag tag : seqA.getTagsByName("20C")) {
            Field20C f = new Field20C(tag.getValue());
            if ("CORP".equals(f.getQualifier())) {
                corpActnGnlInf.setCorpActnEvtId(f.getReference());
                foundCorp = true;
            } else if ("PREV".equals(f.getQualifier())) {
                DocumentIdentification31 prev = new DocumentIdentification31();
                prev.setId(f.getReference());
                ntfctn.setPrvsNtfctnId(prev);
            }
        }
        if (!foundCorp) {
            throw new MT564MissingFieldException("CORP reference is mandatory in Sequence A");
        }

        String funcCodeXml = null;
        for (com.prowidesoftware.swift.model.Tag tag : seqA.getTagsByName("23G")) {
            String val = tag.getValue();
            String function = val;
            String subfunction = null;
            if (val.contains("//")) {
                String[] parts = val.split("//");
                function = parts[0];
                if (parts.length > 1) subfunction = parts[1];
            } else if (val.contains("/")) {
                String[] parts = val.split("/");
                function = parts[0];
                if (parts.length > 1) subfunction = parts[1];
            }
            
            funcCodeXml = function;
            if ("REPL".equals(function)) {
                isRepl = true;
            }
            
            // SMPG rule PROC//ENTL
            if ("ENTL".equals(subfunction)) {
                isEntl = true;
                if (!"NEWM".equals(function)) {
                    result.addWarning("PROC//ENTL status is only allowed with NEWM function");
                }
            }
        }
        
        if (isRepl && ntfctn.getPrvsNtfctnId() == null) {
            result.addWarning("Always include PREV linkage reference when sending a REPL message");
        }

        // Search for Event Type in the entire message since it can appear in different sequences
        for (com.prowidesoftware.swift.model.Tag tag : mt564.getSwiftMessage().getBlock4().getTagsByName("22F")) {
            Field22F f = new Field22F(tag.getValue());
            if ("CAEV".equals(f.getQualifier())) {
                CorporateActionEventType84Choice evtTp = new CorporateActionEventType84Choice();
                try {
                    CorporateActionEventType31Code code = CorporateActionEventType31Code.valueOf(f.getIndicator());
                    evtTp.setCd(code);
                    corpActnGnlInf.setEvtTp(evtTp);
                } catch (Exception e) {
                    result.addWarning("Unknown Event Type: " + f.getIndicator());
                }
            } else if ("CAMV".equals(f.getQualifier())) {
                CorporateActionMandatoryVoluntary3Choice camv = new CorporateActionMandatoryVoluntary3Choice();
                try {
                    CorporateActionMandatoryVoluntary1Code code = CorporateActionMandatoryVoluntary1Code.valueOf(f.getIndicator());
                    camv.setCd(code);
                    corpActnGnlInf.setMndtryVlntryEvtTp(camv);
                } catch (Exception e) {
                    result.addWarning("Unknown CAMV code: " + f.getIndicator());
                }
            } else if ("ADDB".equals(f.getQualifier()) && "CAPA".equals(f.getIndicator())) {
                result.addWarning("ADDB//CAPA indicates preliminary advice of payment (should link to seev.035)");
            }
        }

        // Sequence B - Security ID (global underlying)
        if (seqB != null) {
            for (com.prowidesoftware.swift.model.Tag tag : seqB.getTagsByName("35B")) {
                Field35B isinField = new Field35B(tag.getValue());
                SecurityIdentification19 secId = new SecurityIdentification19();
                secId.setISIN(isinField.getISIN());
                
                FinancialInstrumentAttributes108 undrlygScty = new FinancialInstrumentAttributes108();
                undrlygScty.setFinInstrmId(secId);
                corpActnGnlInf.setUndrlygScty(undrlygScty);
                break;
            }
        }
        
        // Ensure <seev:DtDtls> is available to replace later
        CorporateAction60 corpActnDtls = new CorporateAction60();
        CorporateActionDate61 initialDates = new CorporateActionDate61();
        corpActnDtls.setDtDtls(initialDates);
        ntfctn.setCorpActnDtls(corpActnDtls);

        // SMPG Rule: PROC//ENTL requires Sequence E1 or E2
        boolean hasE = (mt564.getSequenceE1List() != null && !mt564.getSequenceE1List().isEmpty()) ||
                       (mt564.getSequenceE2List() != null && !mt564.getSequenceE2List().isEmpty());
        if (isEntl && !hasE) {
             result.addWarning("PROC//ENTL requires E1 or E2 sequence present");
        }

        // Generate base XML
        String xml = mx.message();
        
        // -------------------------------------------------------------
        // CUSTOM PARSING: sequential block 4 extractor
        // -------------------------------------------------------------
        
        StringBuilder optionsXml = new StringBuilder();
        StringBuilder dtDtlsXml = new StringBuilder();
        
        boolean inCaoptn = false;
        
        // Option state variables
        String optnNb = null;
        String optnTp = null;
        String dflt = null;
        String rspnDdln = null;
        String mktDdln = null;
        String mktDdlnTm = null;
        String pmtDt = null;
        
        List<String[]> cshMvmnts = new ArrayList<>(); // [qualifier, ccy, amt]
        List<String[]> rateMvmnts = new ArrayList<>(); // [qualifier, rate]
        List<String[]> secMvmnts = new ArrayList<>(); // unused right now as we just track states
        
        String currentIsin = null; // 35B inside CAOPTN
        String secQty = null;
        String secQtyType = null;
        
        // Global variables
        String anncmntDt = null;
        String xDte = null;
        String rcrdDt = null;
        String pmtDtGnl = null;
        String efctvDt = null;
        String xpryDt = null;
        String mtrtyDt = null;
        String sspdDt = null;
        String mktDdlnGnl = null;
        String elctnStart = null;
        String elctnEnd = null;
        
        String coaf = null;
        String seme = null;
        String proc = null;
        String srdc = null;
        List<String> adtxLines = new ArrayList<>();
        String webb = null;
        
        for (com.prowidesoftware.swift.model.Tag tag : b4.getTags()) {
            String name = tag.getName();
            String val = tag.getValue();
            
            if ("16R".equals(name) && "CAOPTN".equals(val)) {
                inCaoptn = true;
                optnNb = null; optnTp = null; dflt = null; rspnDdln = null; mktDdln = null; mktDdlnTm = null; pmtDt = null;
                cshMvmnts.clear(); rateMvmnts.clear(); secMvmnts.clear();
                currentIsin = null; secQty = null; secQtyType = null;
                continue;
            }
            if ("16S".equals(name) && "CAOPTN".equals(val)) {
                inCaoptn = false;
                
                // Build the option XML block
                StringBuilder opt = new StringBuilder();
                opt.append("    <seev:CorpActnOptn>\n");
                if (optnNb != null) opt.append("        <seev:OptnNb>").append(optnNb).append("</seev:OptnNb>\n");
                if (optnTp != null) opt.append("        <seev:OptnTp><seev:Cd>").append(optnTp).append("</seev:Cd></seev:OptnTp>\n");
                if (dflt != null) opt.append("        <seev:DfltPrcgCtgy>").append(dflt).append("</seev:DfltPrcgCtgy>\n");
                
                if (rspnDdln != null || mktDdln != null || mktDdlnTm != null) {
                    opt.append("        <seev:DdlnDtls>\n");
                    if (rspnDdln != null) opt.append("            <seev:RspnDdln><seev:Dt>").append(rspnDdln).append("</seev:Dt></seev:RspnDdln>\n");
                    if (mktDdln != null) opt.append("            <seev:MktDdln><seev:Dt>").append(mktDdln).append("</seev:Dt></seev:MktDdln>\n");
                    else if (mktDdlnTm != null) opt.append("            <seev:MktDdln><seev:DtTm>").append(mktDdlnTm).append("</seev:DtTm></seev:MktDdln>\n");
                    opt.append("        </seev:DdlnDtls>\n");
                }
                
                // Cash Movements
                for (String[] csh : cshMvmnts) {
                    String qual = csh[0];
                    String ccy = csh[1];
                    String amt = csh[2];
                    opt.append("        <seev:CshMvmntDtls>\n");
                    opt.append("            <seev:Amt Ccy=\"").append(ccy).append("\">").append(amt).append("</seev:Amt>\n");
                    opt.append("            <seev:CdtDbtInd>CRDT</seev:CdtDbtInd>\n");
                    
                    if (pmtDt != null) {
                        opt.append("            <seev:DtDtls><seev:PmtDt><seev:Dt>").append(pmtDt).append("</seev:Dt></seev:PmtDt></seev:DtDtls>\n");
                    }
                    opt.append("        </seev:CshMvmntDtls>\n");
                }
                
                // Securities Movement
                if (secQty != null || currentIsin != null) {
                    opt.append("        <seev:SctiesMvmntDtls>\n");
                    if (currentIsin != null) {
                        opt.append("            <seev:FinInstrmId><seev:ISIN>").append(currentIsin).append("</seev:ISIN></seev:FinInstrmId>\n");
                    }
                    if (secQty != null) {
                        opt.append("            <seev:Qty>\n");
                        if ("UNIT".equals(secQtyType)) opt.append("                <seev:Unit>").append(secQty).append("</seev:Unit>\n");
                        else opt.append("                <seev:FaceAmt>").append(secQty).append("</seev:FaceAmt>\n");
                        opt.append("            </seev:Qty>\n");
                    }
                    opt.append("        </seev:SctiesMvmntDtls>\n");
                }
                
                // Rate Details
                if (!rateMvmnts.isEmpty()) {
                    opt.append("        <seev:RateDtls>\n");
                    for (String[] rt : rateMvmnts) {
                        String qual = rt[0];
                        String rate = rt[1];
                        
                        String matchedCcy = "XXX";
                        for (String[] csh : cshMvmnts) {
                            if (csh[0].equals(qual)) {
                                matchedCcy = csh[1];
                                break;
                            }
                        }
                        
                        if ("GRSS".equals(qual)) opt.append("            <seev:GrssDvddRate><seev:Amt Ccy=\"").append(matchedCcy).append("\">").append(rate).append("</seev:Amt></seev:GrssDvddRate>\n");
                        else if ("NETT".equals(qual)) opt.append("            <seev:NetDvddRate><seev:Amt Ccy=\"").append(matchedCcy).append("\">").append(rate).append("</seev:Amt></seev:NetDvddRate>\n");
                        else if ("WITF".equals(qual)) opt.append("            <seev:TaxRltdRate><seev:Rate>").append(rate).append("</seev:Rate></seev:TaxRltdRate>\n");
                        else if ("ADEX".equals(qual)) opt.append("            <seev:AddtlQty><seev:Qty>").append(rate).append("</seev:Qty></seev:AddtlQty>\n");
                        else if ("NEWO".equals(qual)) opt.append("            <seev:NewToOd><seev:Qty>").append(rate).append("</seev:Qty></seev:NewToOd>\n");
                        else if ("INDI".equals(qual)) opt.append("            <seev:IndxFctr>").append(rate).append("</seev:IndxFctr>\n");
                        else if ("PRFC".equals(qual)) opt.append("            <seev:PricFctr>").append(rate).append("</seev:PricFctr>\n");
                    }
                    opt.append("        </seev:RateDtls>\n");
                }
                
                opt.append("    </seev:CorpActnOptn>\n");
                optionsXml.append(opt.toString());
                continue;
            }
            
            if (inCaoptn) {
                if ("13A".equals(name)) {
                    int idx = val.indexOf("//");
                    if (idx != -1 && val.substring(0, idx).replace(":", "").equals("CAON")) {
                        optnNb = val.substring(idx + 2);
                        if (optnNb != null) {
                            while (optnNb.length() < 3) optnNb = "0" + optnNb;
                        }
                    }
                } else if ("22F".equals(name)) {
                    Field22F f = new Field22F(val);
                    if ("CAOP".equals(f.getQualifier())) optnTp = f.getIndicator();
                } else if ("17B".equals(name)) {
                    int idx = val.indexOf("//");
                    if (idx != -1 && val.substring(0, idx).replace(":", "").equals("DFLT")) {
                        dflt = "Y".equals(val.substring(idx + 2)) ? "DRCT" : null;
                    }
                } else if ("98A".equals(name)) {
                    Field98A f = new Field98A(val);
                    if ("RDDT".equals(f.getQualifier())) rspnDdln = formatIsoDate(f.getDate());
                    else if ("MKDT".equals(f.getQualifier())) mktDdln = formatIsoDate(f.getDate());
                    else if ("PAYD".equals(f.getQualifier())) pmtDt = formatIsoDate(f.getDate());
                } else if ("98E".equals(name)) {
                    Field98E f = new Field98E(val);
                    if ("MKDT".equals(f.getQualifier())) mktDdlnTm = formatIsoDateTime(f.getValue()); // RAW VALUE passed to handle format
                } else if ("19B".equals(name)) {
                    String[] parsed = parse19B(val);
                    if (parsed != null) cshMvmnts.add(parsed);
                } else if ("92A".equals(name)) {
                    String[] parsed = parse92A(val);
                    if (parsed != null) rateMvmnts.add(parsed);
                } else if ("36B".equals(name)) {
                    String[] parsed = parse36B(val);
                    if (parsed != null) {
                        secQtyType = parsed[0];
                        secQty = parsed[1];
                    }
                } else if ("35B".equals(name)) {
                    Field35B f = new Field35B(val);
                    currentIsin = f.getISIN();
                }
            } else {
                if ("98A".equals(name)) {
                    Field98A f = new Field98A(val);
                    String q = f.getQualifier();
                    String d = formatIsoDate(f.getDate());
                    if ("ANOU".equals(q)) anncmntDt = d;
                    else if ("XDTE".equals(q)) xDte = d;
                    else if ("RDTE".equals(q) || "RDDT".equals(q)) rcrdDt = d; 
                    else if ("PAVT".equals(q) || "PAYD".equals(q)) pmtDtGnl = d;
                    else if ("EFFD".equals(q)) efctvDt = d;
                    else if ("EXPI".equals(q)) xpryDt = d;
                    else if ("MATU".equals(q)) mtrtyDt = d;
                    else if ("SUSP".equals(q)) sspdDt = d;
                } else if ("98E".equals(name)) {
                    Field98E f = new Field98E(val);
                    if ("MKDT".equals(f.getQualifier())) mktDdlnGnl = formatIsoDateTime(f.getValue());
                } else if ("69A".equals(name)) {
                    int idx = val.indexOf("//");
                    if (idx != -1 && val.substring(0, idx).replace(":", "").equals("PWAL")) {
                        String after = val.substring(idx + 2);
                        String[] parts = after.split("/");
                        if (parts.length == 2) {
                            elctnStart = formatIsoDate(parts[0]);
                            elctnEnd = formatIsoDate(parts[1]);
                        }
                    }
                } else if ("20C".equals(name)) {
                    Field20C f = new Field20C(val);
                    if ("COAF".equals(f.getQualifier())) coaf = f.getReference();
                    else if ("SEME".equals(f.getQualifier())) seme = f.getReference();
                } else if ("25D".equals(name)) {
                    int idx = val.indexOf("//");
                    if (idx != -1 && val.substring(0, idx).replace(":", "").equals("PROC")) {
                        proc = val.substring(idx + 2);
                    }
                } else if ("17B".equals(name)) {
                    int idx = val.indexOf("//");
                    if (idx != -1 && val.substring(0, idx).replace(":", "").equals("SRDC")) {
                        srdc = "Y".equals(val.substring(idx + 2)) ? "true" : "false";
                    }
                } else if ("70E".equals(name)) {
                    if (val.contains("ADTX//")) {
                        int idx = val.indexOf("ADTX//");
                        adtxLines.add(val.substring(idx + 6).replace("\n", " ").replace("\r", ""));
                    }
                } else if ("70G".equals(name)) {
                    if (val.contains("WEBB//")) {
                        int idx = val.indexOf("WEBB//");
                        webb = val.substring(idx + 6).replace("\n", "").replace("\r", "");
                    }
                }
            }
        }
        
        // Finalize DtDtls
        if (anncmntDt != null || xDte != null || rcrdDt != null || pmtDtGnl != null || efctvDt != null || xpryDt != null || mtrtyDt != null || sspdDt != null || mktDdlnGnl != null || elctnStart != null) {
            dtDtlsXml.append("<seev:DtDtls>\n");
            if (anncmntDt != null) dtDtlsXml.append("                <seev:AnncmntDt><seev:Dt>").append(anncmntDt).append("</seev:Dt></seev:AnncmntDt>\n");
            if (xDte != null) dtDtlsXml.append("                <seev:XDt><seev:Dt>").append(xDte).append("</seev:Dt></seev:XDt>\n");
            if (rcrdDt != null) dtDtlsXml.append("                <seev:RcrdDt><seev:Dt>").append(rcrdDt).append("</seev:Dt></seev:RcrdDt>\n");
            if (pmtDtGnl != null) dtDtlsXml.append("                <seev:PmtDt><seev:Dt>").append(pmtDtGnl).append("</seev:Dt></seev:PmtDt>\n");
            if (efctvDt != null) dtDtlsXml.append("                <seev:EfctvDt><seev:Dt>").append(efctvDt).append("</seev:Dt></seev:EfctvDt>\n");
            if (xpryDt != null) dtDtlsXml.append("                <seev:XpryDt><seev:Dt>").append(xpryDt).append("</seev:Dt></seev:XpryDt>\n");
            if (mtrtyDt != null) dtDtlsXml.append("                <seev:MtrtyDt><seev:Dt>").append(mtrtyDt).append("</seev:Dt></seev:MtrtyDt>\n");
            if (sspdDt != null) dtDtlsXml.append("                <seev:SspdDt><seev:Dt>").append(sspdDt).append("</seev:Dt></seev:SspdDt>\n");
            if (mktDdlnGnl != null) dtDtlsXml.append("                <seev:MktDdln><seev:DtTm>").append(mktDdlnGnl).append("</seev:DtTm></seev:MktDdln>\n");
            if (elctnStart != null && elctnEnd != null) {
                dtDtlsXml.append("                <seev:ElctnPrd><seev:FrDt><seev:Dt>").append(elctnStart).append("</seev:Dt></seev:FrDt><seev:ToDt><seev:Dt>").append(elctnEnd).append("</seev:Dt></seev:ToDt></seev:ElctnPrd>\n");
            }
            dtDtlsXml.append("            </seev:DtDtls>");
        }
        
        // Post-processing string replacements
        if (funcCodeXml != null && !xml.contains("<seev:NtfctnTp>")) {
            if (xml.contains("<seev:NtfctnGnlInf></seev:NtfctnGnlInf>")) {
                xml = xml.replace("<seev:NtfctnGnlInf></seev:NtfctnGnlInf>", "<seev:NtfctnGnlInf>\n            <seev:NtfctnTp>" + funcCodeXml + "</seev:NtfctnTp>\n        </seev:NtfctnGnlInf>");
            } else if (xml.contains("<seev:NtfctnGnlInf/>")) {
                xml = xml.replace("<seev:NtfctnGnlInf/>", "<seev:NtfctnGnlInf>\n            <seev:NtfctnTp>" + funcCodeXml + "</seev:NtfctnTp>\n        </seev:NtfctnGnlInf>");
            }
        }
        
        if (seme != null) {
            xml = xml.replace("</seev:NtfctnGnlInf>", "    <seev:NtfctnId>" + seme + "</seev:NtfctnId>\n        </seev:NtfctnGnlInf>");
        }
        
        if (coaf != null) {
            xml = xml.replace("</seev:CorpActnEvtId>", "</seev:CorpActnEvtId>\n            <seev:OfficlCorpActnEvtId>" + coaf + "</seev:OfficlCorpActnEvtId>");
        }
        
        if (dtDtlsXml.length() > 0) {
            if (xml.contains("<seev:DtDtls></seev:DtDtls>")) {
                xml = xml.replace("<seev:DtDtls></seev:DtDtls>", dtDtlsXml.toString());
            } else if (xml.contains("<seev:DtDtls/>")) {
                xml = xml.replace("<seev:DtDtls/>", dtDtlsXml.toString());
            }
        }
        
        StringBuilder topLvl = new StringBuilder();
        if (proc != null) topLvl.append("    <seev:PrcgSts><seev:Cd>").append(proc).append("</seev:Cd></seev:PrcgSts>\n");
        if (srdc != null) topLvl.append("    <seev:ShrhldrRghtsDrctvInd>").append(srdc).append("</seev:ShrhldrRghtsDrctvInd>\n");
        if (!adtxLines.isEmpty() || webb != null) {
            topLvl.append("    <seev:AddtlInf>\n");
            if (!adtxLines.isEmpty()) topLvl.append("        <seev:NrrtvVrsn>").append(String.join(" ", adtxLines)).append("</seev:NrrtvVrsn>\n");
            if (webb != null) topLvl.append("        <seev:UrlAdr>").append(webb).append("</seev:UrlAdr>\n");
            topLvl.append("    </seev:AddtlInf>\n");
        }
        
        if (optionsXml.length() > 0 || topLvl.length() > 0) {
            xml = xml.replace("</seev:CorpActnNtfctn>", optionsXml.toString() + topLvl.toString() + "</seev:CorpActnNtfctn>");
        }
        
        result.setXml(xml);
        result.setSuccess(result.getErrors().isEmpty());
        
        return result;
    }

    // Extraction Utilities
    
    private String formatIsoDate(String d) {
        if (d != null && d.length() == 8) {
            return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8);
        }
        return d;
    }
    
    private String formatIsoDateTime(String val) {
        // e.g. :98E::MKDT//20241210120000/00
        int idx = val.indexOf("//");
        if (idx != -1) {
            String after = val.substring(idx + 2);
            String[] parts = after.split("/");
            if (parts.length == 2 && parts[0].length() == 14) {
                String d = parts[0];
                return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8) + "T" +
                       d.substring(8, 10) + ":" + d.substring(10, 12) + ":" + d.substring(12, 14) + ".000Z";
            }
        }
        return val;
    }
    
    private String[] parse19B(String val) {
        int idx = val.indexOf("//");
        if (idx != -1) {
            String qual = val.substring(0, idx).replace(":", "");
            String after = val.substring(idx + 2);
            if (after.startsWith("N")) after = after.substring(1); // ignore sign for now
            if (after.length() > 3) {
                String ccy = after.substring(0, 3);
                String amt = after.substring(3).replace(",", ".");
                if (amt.endsWith(".")) amt = amt.substring(0, amt.length() - 1);
                return new String[]{qual, ccy, amt};
            }
        }
        return null;
    }
    
    private String[] parse92A(String val) {
        int idx = val.indexOf("//");
        if (idx != -1) {
            String qual = val.substring(0, idx).replace(":", "");
            String rate = val.substring(idx + 2).replace(",", ".");
            if (rate.endsWith(".")) rate = rate.substring(0, rate.length() - 1);
            return new String[]{qual, rate};
        }
        return null;
    }
    
    private String[] parse36B(String val) {
        int idx = val.indexOf("//");
        if (idx != -1) {
            String after = val.substring(idx + 2);
            String[] parts = after.split("/");
            if (parts.length == 2) {
                String type = parts[0];
                String qty = parts[1].replace(",", ".");
                if (qty.endsWith(".")) qty = qty.substring(0, qty.length() - 1);
                return new String[]{type, qty};
            }
        }
        return null;
    }
}
