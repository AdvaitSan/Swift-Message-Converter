import subprocess
from reportlab.lib.pagesizes import letter
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Preformatted
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.lib import colors

def generate_pdf():
    print("Running Java converter...")
    # Run the java converter and capture output
    result = subprocess.run(
        ['java', '-jar', 'target/swift-converter-1.0-SNAPSHOT.jar', 'smpg_real_samples.txt'],
        capture_output=True, text=True
    )
    
    # Read the raw inputs
    with open('smpg_real_samples.txt', 'r') as f:
        raw_content = f.read()
    
    import re
    # Extract raw messages using the same regex
    raw_messages = re.findall(r'\{1:.*?-\}', raw_content, re.DOTALL)
    
    # Parse the java output to find the XML results
    output_text = result.stdout
    converted_blocks = output_text.split("Processing Message #")
    
    doc = SimpleDocTemplate("MT564_Conversion_Report.pdf", pagesize=letter, rightMargin=30, leftMargin=30, topMargin=30, bottomMargin=30)
    styles = getSampleStyleSheet()
    
    title_style = styles['Heading1']
    title_style.alignment = 1 # Center
    
    heading_style = styles['Heading2']
    heading_style.textColor = colors.darkblue
    
    code_style = ParagraphStyle(
        'Code',
        parent=styles['Code'],
        fontSize=8,
        leading=10,
        backColor=colors.whitesmoke,
        borderWidth=1,
        borderColor=colors.lightgrey,
        borderPadding=5,
        wordWrap='CJK'
    )
    
    story = []
    story.append(Paragraph("SWIFT MT564 to ISO 20022 seev.031", title_style))
    story.append(Paragraph("Conversion Verification Report", title_style))
    story.append(Spacer(1, 0.5 * inch))
    
    for i, raw_msg in enumerate(raw_messages):
        msg_idx = i + 1
        
        # Find the corresponding output block
        xml_output = "No Output Found"
        for block in converted_blocks[1:]: # Skip the first empty split
            if block.startswith(str(msg_idx) + "..."):
                if "<?xml" in block:
                    xml_output = "<?xml" + block.split("<?xml")[1]
                    xml_output = xml_output.split("----------------------------------------")[0].strip()
                elif "Validation Error" in block or "Exception" in block:
                    xml_output = block.split("INFO: Starting conversion of MT564")[1].split("----------------------------------------")[0].strip()
                break
                
        story.append(Paragraph(f"Message #{msg_idx}", heading_style))
        story.append(Spacer(1, 0.1 * inch))
        
        story.append(Paragraph("Raw Input (MT564 FIN)", styles['Heading3']))
        story.append(Preformatted(raw_msg.strip(), code_style))
        story.append(Spacer(1, 0.2 * inch))
        
        story.append(Paragraph("Parsed Output (ISO 20022 XML)", styles['Heading3']))
        story.append(Preformatted(xml_output, code_style))
        
        story.append(PageBreak())
        
    print("Building PDF...")
    doc.build(story)
    print("Success! Report saved to MT564_Conversion_Report.pdf")

if __name__ == "__main__":
    generate_pdf()
