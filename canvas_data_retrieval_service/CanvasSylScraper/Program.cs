using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using Newtonsoft.Json;
using UglyToad.PdfPig;
using DocumentFormat.OpenXml.Packaging;
using System.Net.Http;
using Newtonsoft.Json.Linq;
using HtmlAgilityPack;

namespace CanvasSylScraper {
    public class Program {
        private static string _CanvasAPIKey;
        private static string _CanvasAPIUrl = $"https://ferris.instructure.com/api/v1";
        private static string _DownloadDirectory;
        private static bool _DownloadExtraFerrisAddendum;

        // New dictionaries to track course info and syllabus files
        private static Dictionary<string, List<string>> _CourseSyllabusFiles = new Dictionary<string, List<string>>();
        private static Dictionary<string, string> _CourseNames = new Dictionary<string, string>();

        static void Main(string[] args) {

            _DownloadDirectory = args[1];

            GetFilesFromCanvas(args[0]);
            Console.WriteLine("Now Extracting...");
            ExtractJsonFromFiles();
            Console.ReadLine();
        }

        static void GetFilesFromCanvas(string apiKey) {
            if (apiKey.Length == 0) {
                Console.WriteLine("You must pass in your Canvas API as a command line argument.");
                return;
            }

            _CanvasAPIKey = apiKey;

            if (!Directory.Exists(_DownloadDirectory)) {
                Directory.CreateDirectory(_DownloadDirectory);
            }

            try {
                using (HttpClient client = new HttpClient()) {
                    Console.WriteLine("Fetching active courses...");
                    var courses = GetCourses(client);

                    foreach (var course in courses) {
                        string courseName = course["name"]?.ToString();
                        string courseId = course["id"]?.ToString();
                        Console.WriteLine($"\nCourse: {courseName}");

                        // Track course name
                        _CourseNames[courseId] = courseName;

                        if (!courseName.Contains("SENG")) {
                            continue;
                        }

                        var syllabusModules = GetSyllabusModules(client, courseId);
                        foreach (var module in syllabusModules) {
                            string moduleName = module["name"]?.ToString();
                            string moduleId = module["id"]?.ToString();

                            Console.WriteLine($"  Found syllabus module: {moduleName}");

                            if (moduleId == "fallback") {
                                string syllabusBody = module["syllabus_body"]?.ToString();
                                Console.WriteLine("    (No module items – pulled from course syllabus_body)");

                                if (!string.IsNullOrWhiteSpace(syllabusBody)) {
                                    var doc = new HtmlDocument();
                                    doc.LoadHtml(syllabusBody);

                                    var links = doc.DocumentNode.SelectNodes("//a[@href]");
                                    if (links != null && links.Count > 0) {
                                        foreach (var link in links) {
                                            string href = link.GetAttributeValue("href", "");
                                            string text = link.InnerText.Trim();
                                            Console.WriteLine($"    Link: {text} - {href}");
                                        }
                                    }

                                    var plainText = doc.DocumentNode.InnerText;
                                    if (!string.IsNullOrWhiteSpace(plainText)) {
                                        plainText = plainText.Trim();
                                        Console.WriteLine("    Syllabus Text Preview:");
                                        Console.WriteLine("    " +
                                                          plainText.Substring(0, Math.Min(500, (int)plainText.Length))
                                                              .Replace("\n", " ").Replace("\r", " ") + "...");
                                    }
                                }

                                continue;
                            }

                            var syllabusItems = GetSyllabusItems(client, courseId, moduleId);
                            foreach (var item in syllabusItems) {
                                string itemName = item["title"]?.ToString();
                                string type = item["type"]?.ToString();
                                string url = item["external_url"]?.ToString() ?? item["html_url"]?.ToString();

                                if (!_DownloadExtraFerrisAddendum &&
                                    (itemName.Contains("Addendum") || (itemName.Contains("Wide")))) {
                                    continue;
                                }

                                Console.WriteLine($"    Item: {itemName} ({type})");
                                Console.WriteLine($"    URL: {url}");

                                if (type?.ToLower() == "file" && item["content_id"] != null) {
                                    string fileId = item["content_id"].ToString();
                                    var fileInfo = GetFileInfo(client, fileId);
                                    if (fileInfo != null) {
                                        string fileName = fileInfo["display_name"]?.ToString();
                                        string downloadUrl = fileInfo["url"]?.ToString();
                                        if (!string.IsNullOrWhiteSpace(downloadUrl) &&
                                            ((fileName.ToLower().EndsWith(".pdf")) ||
                                             (fileName.ToLower().EndsWith(".docx")))) {
                                            Console.WriteLine("    -> Downloading...");
                                            DownloadFile(client, downloadUrl, fileName, courseId);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception ex) {
                Console.WriteLine("Error: " + ex.Message);
            }

            Console.WriteLine("Done Scraping.");
        }

        static List<JObject> GetCourses(HttpClient client) {
            string url = $"{_CanvasAPIUrl}/courses?enrollment_state=active&per_page=100&access_token={_CanvasAPIKey}";
            return SafeGetJArray(client, url);
        }

        static List<JObject> GetSyllabusModules(HttpClient client, string courseId) {
            string url = $"{_CanvasAPIUrl}/courses/{courseId}/modules?per_page=100&access_token={_CanvasAPIKey}";
            var modules = SafeGetJArray(client, url);

            var matches = new List<JObject>();
            foreach (var module in modules) {
                string name = module["name"]?.ToString().ToLower();
                if (name != null &&
                    (name.Contains("syllabus") || name.Contains("introduction") || name.Contains("start"))) {
                    matches.Add(module);
                }
            }

            if (matches.Count == 0) {
                JObject course = GetCourse(client, courseId);
                string syllabusBody = course?["syllabus_body"]?.ToString();
                if (!string.IsNullOrWhiteSpace(syllabusBody)) {
                    var fallbackModule = new JObject {
                        ["id"] = "fallback",
                        ["name"] = "Course Syllabus (from syllabus_body)",
                        ["syllabus_body"] = syllabusBody
                    };
                    matches.Add(fallbackModule);
                }
            }

            return matches;
        }

        static JObject GetCourse(HttpClient client, string courseId) {
            string url = $"{_CanvasAPIUrl}/courses/{courseId}?access_token={_CanvasAPIKey}";
            try {
                HttpResponseMessage response = client.GetAsync(url).Result;
                string json = response.Content.ReadAsStringAsync().Result;

                if (!response.IsSuccessStatusCode) {
                    Console.WriteLine($"Failed to get course {courseId}: {response.StatusCode} - {json}");
                    return null;
                }

                return JObject.Parse(json);
            }
            catch (Exception ex) {
                Console.WriteLine("Error getting course: " + ex.Message);
                return null;
            }
        }

        static List<JObject> GetSyllabusItems(HttpClient client, string courseId, string moduleId) {
            string url = $"{_CanvasAPIUrl}/courses/{courseId}/modules/{moduleId}/items?access_token={_CanvasAPIKey}";
            var items = SafeGetJArray(client, url);

            var results = new List<JObject>();
            foreach (var item in items) {
                results.Add(item);
            }

            return results;
        }

        static JObject GetFileInfo(HttpClient client, string fileId) {
            string url = $"{_CanvasAPIUrl}/files/{fileId}?access_token={_CanvasAPIKey}";
            try {
                var response = client.GetAsync(url).Result;
                var json = response.Content.ReadAsStringAsync().Result;

                if (!response.IsSuccessStatusCode) {
                    Console.WriteLine($"      Failed to get file info: {response.StatusCode} - {json}");
                    return null;
                }

                return JObject.Parse(json);
            }
            catch (Exception ex) {
                Console.WriteLine("      Error getting file info: " + ex.Message);
                return null;
            }
        }

        static void DownloadFile(HttpClient client, string url, string filename, string courseId) {
            try {
                string safeFilename = string.Join("_", filename.Split(Path.GetInvalidFileNameChars()));
                string path = Path.Combine(_DownloadDirectory, safeFilename);

                var request = new HttpRequestMessage(HttpMethod.Get, url);
                request.Headers.Add("Authorization", $"Bearer {_CanvasAPIKey}");

                var response = client.SendAsync(request).Result;

                if (!response.IsSuccessStatusCode) {
                    Console.WriteLine($"      Failed to download file: {response.StatusCode}");
                    return;
                }

                var fileBytes = response.Content.ReadAsByteArrayAsync().Result;
                File.WriteAllBytes(path, fileBytes);
                Console.WriteLine($"      File saved: {path}");

                // Track this syllabus file for the course
                if (!_CourseSyllabusFiles.ContainsKey(courseId))
                    _CourseSyllabusFiles[courseId] = new List<string>();

                _CourseSyllabusFiles[courseId].Add(path);
            }
            catch (Exception ex) {
                Console.WriteLine("      Error downloading file: " + ex.Message);
            }
        }

        static List<JObject> SafeGetJArray(HttpClient client, string url) {
            var items = new List<JObject>();

            try {
                HttpResponseMessage response = client.GetAsync(url).Result;
                string json = response.Content.ReadAsStringAsync().Result;

                if (!response.IsSuccessStatusCode) {
                    Console.WriteLine($"Canvas API error {response.StatusCode}: {json}");
                    return items;
                }

                try {
                    JArray array = JArray.Parse(json);
                    foreach (var item in array) {
                        items.Add((JObject)item);
                    }
                }
                catch {
                    Console.WriteLine("Warning: Response was not an array. Attempting to parse as object...");
                    try {
                        JObject obj = JObject.Parse(json);
                        Console.WriteLine("Response Object:\n" + obj.ToString());
                    }
                    catch {
                        Console.WriteLine("Failed to parse response as either array or object.");
                    }
                }
            }
            catch (Exception ex) {
                Console.WriteLine("Unexpected error calling Canvas API: " + ex.Message);
            }

            return items;
        }

        static List<object> GetAssignments(HttpClient client, string courseId) {
            string url = $"{_CanvasAPIUrl}/courses/{courseId}/assignments?per_page=100&access_token={_CanvasAPIKey}";
            var results = new List<object>();

            var assignments = SafeGetJArray(client, url);
            foreach (var a in assignments) {
                results.Add(new {
                    Name = a["name"]?.ToString(),
                    DueDate = a["due_at"]?.ToString()
                });
            }

            return results;
        }
        static void ExtractJsonFromFiles() {
            if (_DownloadDirectory.Length == 0 || !Directory.Exists(_DownloadDirectory)) {
                Console.WriteLine("Please provide a valid directory path.");
                return;
            }

            string outputFilePath = Path.Combine(_DownloadDirectory, "output.json");

            try {
                var jsonObjects = new List<object>();

                using (HttpClient client = new HttpClient()) {
                    foreach (var kvp in _CourseSyllabusFiles) {
                        string courseId = kvp.Key;
                        string courseName = _CourseNames.ContainsKey(courseId) ? _CourseNames[courseId] : courseId;

                        // Merge syllabus text
                        var syllabusTexts = new List<string>();
                        foreach (string file in kvp.Value) {
                            string text = ExtractText(file);
                            if (!string.IsNullOrWhiteSpace(text))
                                syllabusTexts.Add(text);
                        }

                        // Get assignments
                        var assignments = GetAssignments(client, courseId);

                        jsonObjects.Add(new {
                            ClassName = courseName,
                            SyllabusText = string.Join("\n", syllabusTexts),
                            Assignments = assignments
                        });
                    }
                }

                string jsonOutput = JsonConvert.SerializeObject(jsonObjects, Formatting.Indented);
                File.WriteAllText(outputFilePath, jsonOutput);

                Console.WriteLine("Extraction complete. Output saved to:");
                Console.WriteLine(outputFilePath);
            }
            catch (Exception ex) {
                Console.WriteLine("Error: " + ex.Message);
            }
        }

        static string ExtractText(string path) {
            string ext = Path.GetExtension(path).ToLowerInvariant();
            switch (ext) {
                case ".pdf":
                    return ExtractTextFromPdf(path);
                case ".docx":
                    return ExtractTextFromDocx(path);
                default:
                    return null;
            }
        }

        static string ExtractTextFromPdf(string path) {
            try {
                using (var document = PdfDocument.Open(path))
                using (var writer = new StringWriter()) {
                    foreach (var page in document.GetPages()) {
                        writer.WriteLine(page.Text);
                    }

                    return writer.ToString();
                }
            }
            catch {
                return null;
            }
        }


        static string ExtractTextFromDocx(string path) {
            try {
                using (var fileStream = File.Open(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
                using (var memStream = new MemoryStream()) {
                    fileStream.CopyTo(memStream);
                    memStream.Position = 0;

                    using (var doc = WordprocessingDocument.Open(memStream, false)) // false = read-only
                    {
                        // Extract all text elements and join them
                        var bodyText = doc.MainDocumentPart.Document
                            .Descendants<DocumentFormat.OpenXml.Wordprocessing.Text>()
                            .Select(t => t.Text)
                            .Where(t => !string.IsNullOrWhiteSpace(t));

                        return string.Join(Environment.NewLine, bodyText);
                    }
                }
            }
            catch (Exception ex) {
                Console.WriteLine($"DOCX error ({path}): {ex.Message}");
                return null;
            }
        }
    }
}