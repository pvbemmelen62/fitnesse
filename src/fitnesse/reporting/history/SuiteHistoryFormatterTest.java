package fitnesse.reporting.history;

import fitnesse.FitNesseContext;
import fitnesse.FitNesseVersion;
import fitnesse.reporting.SuiteExecutionReport.PageHistoryReference;
import fitnesse.testsystems.TestSummary;
import fitnesse.testrunner.WikiTestPage;
import fitnesse.testutil.FitNesseUtil;
import fitnesse.wiki.mem.InMemoryPage;
import fitnesse.wiki.WikiPage;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import util.Clock;
import util.DateAlteringClock;
import util.DateTimeUtil;
import util.TimeMeasurement;
import util.XmlUtil;

import java.io.StringWriter;
import java.util.Date;
import java.util.List;

public class SuiteHistoryFormatterTest {
  private SuiteHistoryFormatter formatter;
  private WikiPage root;
  private FitNesseContext context;
  private WikiTestPage testPage;
  private StringWriter writer;
  private Date testTime;
  private WikiPage suitePage;
  private DateAlteringClock clock;

  @Before
  public void setup() throws Exception {
    testTime = DateTimeUtil.getDateFromString("12/5/1952 1:19:00");
    clock = new DateAlteringClock(testTime).freeze();

    root = InMemoryPage.makeRoot("RooT");
    context = FitNesseUtil.makeTestContext(root);
    suitePage = root.addChildPage("SuitePage");
    testPage = new WikiTestPage(suitePage.addChildPage("TestPage"));
    writer = new StringWriter();
    formatter = new SuiteHistoryFormatter(context, suitePage, writer);
  }

  @After
  public void restoreDefaultClock() {
    Clock.restoreDefaultClock();
  }

  @Test
  public void shouldRememberTestSummariesInReferences() throws Exception {
    performTest(13);
    List<PageHistoryReference> references = formatter.getPageHistoryReferences();
    assertEquals(1, references.size());
    PageHistoryReference pageHistoryReference = references.get(0);
    assertEquals(new TestSummary(1, 2, 3, 4), pageHistoryReference.getTestSummary());
    assertEquals(13, pageHistoryReference.getRunTimeInMillis());
  }

  private void performTest(long elapsedTime) throws Exception {
    formatter.testSystemStarted(null);
    formatter.newTestStarted(testPage);
    clock.elapse(elapsedTime);
    formatter.testComplete(testPage, new TestSummary(1, 2, 3, 4), null);
    formatter.allTestingComplete(null);
  }

  @Test
  public void allTestingCompleteShouldProduceLinksAndSetTotalRunTimeOnReport() throws Exception {
    performTest(13);
    assertEquals(13L, formatter.getSuiteExecutionReport().getTotalRunTimeInMillis());
    
    String output = writer.toString();
    Document document = XmlUtil.newDocument(output);
    Element suiteResultsElement = document.getDocumentElement();
    assertEquals("suiteResults", suiteResultsElement.getNodeName());
    assertEquals(new FitNesseVersion().toString(), XmlUtil.getTextValue(suiteResultsElement, "FitNesseVersion"));
    assertEquals("SuitePage", XmlUtil.getTextValue(suiteResultsElement, "rootPath"));

    NodeList xmlPageReferences = suiteResultsElement.getElementsByTagName("pageHistoryReference");
    assertEquals(1, xmlPageReferences.getLength());
    for (int referenceIndex = 0; referenceIndex < xmlPageReferences.getLength(); referenceIndex++) {
      Element pageHistoryReferenceElement = (Element) xmlPageReferences.item(referenceIndex);
      assertEquals("SuitePage.TestPage", XmlUtil.getTextValue(pageHistoryReferenceElement, "name"));
      assertEquals(DateTimeUtil.formatDate(testTime), XmlUtil.getTextValue(pageHistoryReferenceElement, "date"));
      String link = "SuitePage.TestPage?pageHistory&resultDate=19521205011900";
      assertEquals(link, XmlUtil.getTextValue(pageHistoryReferenceElement, "pageHistoryLink"));
      Element countsElement = XmlUtil.getElementByTagName(pageHistoryReferenceElement, "counts");
      assertEquals("1", XmlUtil.getTextValue(countsElement, "right"));
      assertEquals("2", XmlUtil.getTextValue(countsElement, "wrong"));
      assertEquals("3", XmlUtil.getTextValue(countsElement, "ignores"));
      assertEquals("4", XmlUtil.getTextValue(countsElement, "exceptions"));
      assertEquals("13", XmlUtil.getTextValue(pageHistoryReferenceElement, "runTimeInMillis"));
    }

    Element finalCounts = XmlUtil.getElementByTagName(suiteResultsElement, "finalCounts");
    assertEquals("0", XmlUtil.getTextValue(finalCounts, "right"));
    assertEquals("1", XmlUtil.getTextValue(finalCounts, "wrong"));
    assertEquals("0", XmlUtil.getTextValue(finalCounts, "ignores"));
    assertEquals("0", XmlUtil.getTextValue(finalCounts, "exceptions"));
    
    assertEquals(String.valueOf(13L),
        XmlUtil.getTextValue(suiteResultsElement, "totalRunTimeInMillis"));
  }
}
