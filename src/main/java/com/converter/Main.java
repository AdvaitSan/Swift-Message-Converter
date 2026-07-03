package com.converter;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar swift-converter-1.0-SNAPSHOT.jar <path_to_mt564_file>");
            System.exit(1);
        }
        
        String filePath = args[0];
        try {
            String fullContent = new String(Files.readAllBytes(Paths.get(filePath)));
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{1:.*?-\\}", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher matcher = pattern.matcher(fullContent);
            java.util.List<String> messages = new java.util.ArrayList<>();
            while (matcher.find()) {
                messages.add(matcher.group());
            }
            
            if (messages.isEmpty()) {
                System.out.println("No SWIFT MT messages found in the file.");
                System.exit(0);
            }
            
            CorpActnNtfctnConverter converter = new CorpActnNtfctnConverter();
            
            for (int i = 0; i < messages.size(); i++) {
                String cleanStr = messages.get(i).trim();
                
                System.out.println("Processing Message #" + (i + 1) + "...");
                
                try {
                    ConversionResult result = converter.convert(cleanStr);
                    
                    if (!result.getWarnings().isEmpty()) {
                        System.out.println("--- Warnings ---");
                        for (String warning : result.getWarnings()) {
                            System.out.println("WARN: " + warning);
                        }
                    }
                    
                    if (!result.getErrors().isEmpty()) {
                        System.err.println("--- Errors ---");
                        for (String error : result.getErrors()) {
                            System.err.println("ERROR: " + error);
                        }
                    }
                    
                    if (result.isSuccess()) {
                        System.out.println("--- Conversion Successful ---");
                        System.out.println(result.getXml());
                    } else {
                        System.err.println("--- Conversion Failed ---");
                    }
                } catch (MT564MissingFieldException e) {
                    System.err.println("Validation Error: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("An unexpected error occurred during conversion of message #" + (i + 1) + ": " + e.getMessage());
                    e.printStackTrace();
                }
                System.out.println("----------------------------------------\n");
            }
            
        } catch (Exception e) {
            System.err.println("Failed to read file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
