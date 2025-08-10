This is the Canvas Api Accessing service,

To use this application, simply pass in your Canvas API key, and the output location as parameters to the executable

As an example call, ...\canvas_data_retrieval_service\CanvasSylScraper\bin\Debug\CanvasSylScraper.exe "YOUR_API_KEY" "C:\YourIntendedOutputDirectory"

Calling this executable will scrape all "SENG" courses you are or have been enrolled in. It will fetch any syllabus modules -> then syllabus files (DOCX or PDF) -> then all Assignments and Due Dates for each class

This program will output a JSON file with ClassName, SyllabusText, Assignments and Due Dates
