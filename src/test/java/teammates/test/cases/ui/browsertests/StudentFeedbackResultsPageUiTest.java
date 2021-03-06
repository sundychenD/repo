package teammates.test.cases.ui.browsertests;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import teammates.common.datatransfer.DataBundle;
import teammates.common.datatransfer.StudentAttributes;
import teammates.common.util.Const;
import teammates.common.util.Url;
import teammates.test.driver.BackDoor;
import teammates.test.driver.TestProperties;
import teammates.test.pageobjects.AppPage;
import teammates.test.pageobjects.Browser;
import teammates.test.pageobjects.BrowserPool;
import teammates.test.pageobjects.LoginPage;
import teammates.test.pageobjects.StudentCourseJoinConfirmationPage;
import teammates.test.pageobjects.StudentFeedbackResultsPage;

/**
 * Tests 'Feedback Results' view of students.
 * SUT: {@link StudentFeedbackResultsPage}.
 */
public class StudentFeedbackResultsPageUiTest extends BaseUiTestCase {
    private static DataBundle testData;
    private static Browser browser;
    private StudentFeedbackResultsPage resultsPage;

    @BeforeClass
    public static void classSetup() throws Exception {
        printTestClassHeader();
        testData = loadDataBundle("/StudentFeedbackResultsPageUiTest.json");
        removeAndRestoreTestDataOnServer(testData);
        browser = BrowserPool.getBrowser();
    }

    @Test
    public void testAll() throws Exception {

        ______TS("unreg student");

        AppPage.logout(browser);
        
        // Open Session
        StudentAttributes unreg = testData.students.get("DropOut");
        resultsPage = loginToStudentFeedbackResultsPage(unreg, "Open Session", StudentFeedbackResultsPage.class);
        resultsPage.verifyHtmlMainContent("/unregisteredStudentFeedbackResultsPageOpen.html");

        // Mcq Session
        resultsPage = loginToStudentFeedbackResultsPage(unreg, "MCQ Session", StudentFeedbackResultsPage.class);

        // This is the full HTML verification for Unregistered Student Feedback Results Page, the rest can all be verifyMainHtml
        resultsPage.verifyHtml("/unregisteredStudentFeedbackResultsPageMCQ.html");

        ______TS("no responses");

        resultsPage = loginToStudentFeedbackResultsPage("Alice", "Empty Session");

        // This is the full HTML verification for Registered Student Feedback Results Page, the rest can all be verifyMainHtml
        resultsPage.verifyHtml("/studentFeedbackResultsPageEmpty.html");

        ______TS("standard session results");

        resultsPage = loginToStudentFeedbackResultsPage("Alice", "Open Session");
        resultsPage.verifyHtmlMainContent("/studentFeedbackResultsPageOpen.html");

        ______TS("team-to-team session results");

        resultsPage = loginToStudentFeedbackResultsPage("Benny", "Open Session");
        resultsPage.verifyHtmlMainContent("/studentFeedbackResultsPageTeamToTeam.html");

        ______TS("MCQ session results");

        resultsPage = loginToStudentFeedbackResultsPage("Alice", "MCQ Session");
        resultsPage.verifyHtmlMainContent("/studentFeedbackResultsPageMCQ.html");

        assertEquals("[more]", resultsPage.getQuestionAdditionalInfoButtonText(4, ""));
        assertEquals(true, resultsPage.clickQuestionAdditionalInfoButton(4, ""));
        assertEquals("[less]", resultsPage.getQuestionAdditionalInfoButtonText(4, ""));
        assertEquals(false, resultsPage.clickQuestionAdditionalInfoButton(4, ""));
        assertEquals("[more]", resultsPage.getQuestionAdditionalInfoButtonText(4, ""));

        assertEquals("[more]", resultsPage.getQuestionAdditionalInfoButtonText(5, ""));
        assertEquals(true, resultsPage.clickQuestionAdditionalInfoButton(5, ""));
        assertEquals("[less]", resultsPage.getQuestionAdditionalInfoButtonText(5, ""));
        assertEquals(false, resultsPage.clickQuestionAdditionalInfoButton(5, ""));
        assertEquals("[more]", resultsPage.getQuestionAdditionalInfoButtonText(5, ""));

        ______TS("MSQ session results");

        resultsPage = loginToStudentFeedbackResultsPage("Alice", "MSQ Session");
        resultsPage.verifyHtmlMainContent("/studentFeedbackResultsPageMSQ.html");

        assertEquals("[more]", resultsPage.getQuestionAdditionalInfoButtonText(4, ""));
        assertEquals(true, resultsPage.clickQuestionAdditionalInfoButton(4, ""));
        assertEquals("[less]", resultsPage.getQuestionAdditionalInfoButtonText(4, ""));
        assertEquals(false, resultsPage.clickQuestionAdditionalInfoButton(4, ""));
        assertEquals("[more]", resultsPage.getQuestionAdditionalInfoButtonText(4, ""));

        assertEquals("[more]", resultsPage.getQuestionAdditionalInfoButtonText(5, ""));
        assertEquals(true, resultsPage.clickQuestionAdditionalInfoButton(5, ""));
        assertEquals("[less]", resultsPage.getQuestionAdditionalInfoButtonText(5, ""));
        assertEquals(false, resultsPage.clickQuestionAdditionalInfoButton(5, ""));
        assertEquals("[more]", resultsPage.getQuestionAdditionalInfoButtonText(5, ""));

        ______TS("NUMSCALE session results");

        resultsPage = loginToStudentFeedbackResultsPage("Alice", "NUMSCALE Session");
        resultsPage.verifyHtmlMainContent("/studentFeedbackResultsPageNUMSCALE.html");

        ______TS("CONSTSUM session results");

        resultsPage = loginToStudentFeedbackResultsPage("Alice", "CONSTSUM Session");
        resultsPage.verifyHtmlMainContent("/studentFeedbackResultsPageCONSTSUM.html");


        ______TS("CONTRIB session results");

        resultsPage = loginToStudentFeedbackResultsPage("Alice", "CONTRIB Session");
        resultsPage.verifyHtmlMainContent("/studentFeedbackResultsPageCONTRIB.html");

        ______TS("unreg student logged in as a student in another course: registered after logging out");
        
        String student1Username = TestProperties.inst().TEST_STUDENT1_ACCOUNT; 
        String student1Password = TestProperties.inst().TEST_STUDENT1_PASSWORD;
        
        AppPage.logout(browser);
        LoginPage loginPage = resultsPage.clickLoginAsStudentButton();
        loginPage.loginAsStudent(student1Username, student1Password);

        StudentCourseJoinConfirmationPage confirmationPage = 
                loginToStudentFeedbackResultsPage(unreg, "Open Session", StudentCourseJoinConfirmationPage.class);
        confirmationPage.verifyHtmlMainContent("/studentCourseJoinConfirmationLoggedInHTML.html");
        loginPage = confirmationPage.clickCancelButton();
        loginPage.loginAsStudent(student1Username, student1Password, StudentFeedbackResultsPage.class);

        resultsPage.verifyHtmlMainContent("/studentFeedbackResultsPageNewlyRegistered.html");
        
        BackDoor.editStudent(unreg.email, unreg); // clear the googleId
        
        ______TS("unreg student logged in as a student in another course: registered without logging out");
        
        AppPage.logout(browser);
        loginPage = resultsPage.clickLoginAsStudentButton();
        loginPage.loginAsStudent(student1Username, student1Password);

        confirmationPage = 
                loginToStudentFeedbackResultsPage(unreg, "Open Session", StudentCourseJoinConfirmationPage.class);
        confirmationPage.verifyHtmlMainContent("/studentCourseJoinConfirmationLoggedInHTML.html");
        resultsPage = confirmationPage.clickConfirmButton(StudentFeedbackResultsPage.class);

        resultsPage.verifyHtmlMainContent("/studentFeedbackResultsPageNewlyRegistered.html");
        
        BackDoor.deleteStudent(unreg.course, unreg.email);
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        BrowserPool.release(browser);
    }

    private StudentFeedbackResultsPage loginToStudentFeedbackResultsPage(String studentName, String fsName) {
        Url editUrl = createUrl(Const.ActionURIs.STUDENT_FEEDBACK_RESULTS_PAGE)
                                        .withUserId(testData.students.get(studentName).googleId)
                                        .withCourseId(testData.feedbackSessions.get(fsName).courseId)
                                        .withSessionName(testData.feedbackSessions.get(fsName).feedbackSessionName);
        return loginAdminToPage(browser, editUrl,StudentFeedbackResultsPage.class);
    }

    private <T extends AppPage> T loginToStudentFeedbackResultsPage(StudentAttributes s, String fsDataId,
                                                                    Class<T> typeOfPage) {
        String submitUrl = createUrl(Const.ActionURIs.STUDENT_FEEDBACK_RESULTS_PAGE)
                                            .withCourseId(s.course)
                                            .withStudentEmail(s.email)
                                            .withSessionName(testData.feedbackSessions.get(fsDataId).feedbackSessionName)
                                            .withRegistrationKey(BackDoor.getKeyForStudent(s.course, s.email))
                                .toString();
        browser.driver.get(submitUrl);
        return AppPage.getNewPageInstance(browser, typeOfPage);
    }
}
