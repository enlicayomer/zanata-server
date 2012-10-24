package org.zanata.webtrans.server.rpc;

import org.dbunit.operation.DatabaseOperation;
import org.hamcrest.Matchers;
import org.hibernate.search.impl.FullTextSessionImpl;
import org.hibernate.search.jpa.impl.FullTextEntityManagerImpl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.zanata.ZanataDbunitJpaTest;
import org.zanata.common.LocaleId;
import org.zanata.dao.DocumentDAO;
import org.zanata.dao.ProjectIterationDAO;
import org.zanata.dao.TextFlowDAO;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.TestFixture;
import org.zanata.rest.service.ResourceUtils;
import org.zanata.seam.SeamAutowire;
import org.zanata.security.ZanataIdentity;
import org.zanata.service.LocaleService;
import org.zanata.service.TextFlowSearchService;
import org.zanata.service.impl.TextFlowSearchServiceImpl;
import org.zanata.webtrans.client.service.GetTransUnitActionContext;
import org.zanata.webtrans.shared.auth.EditorClientId;
import org.zanata.webtrans.shared.model.DocumentId;
import org.zanata.webtrans.shared.model.ProjectIterationId;
import org.zanata.webtrans.shared.rpc.GetTransUnitList;
import org.zanata.webtrans.shared.rpc.GetTransUnitListResult;

import lombok.extern.slf4j.Slf4j;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Patrick Huang <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@Test(groups = { "jpa-tests" })
@Slf4j
public class GetTransUnitListHandlerTest extends ZanataDbunitJpaTest
{
   private GetTransUnitListHandler handler;
   @Mock
   private ZanataIdentity identity;
   @Mock
   private LocaleService localeService;
   private final DocumentId documentId = new DocumentId(1);
   private final LocaleId localeId = new LocaleId("ja");
   private HLocale jaHLocale;

   @Override
   protected void prepareDBUnitOperations()
   {
      beforeTestOperations.add(new DataSetOperation("performance/GetTransUnitListTest.dbunit.xml", DatabaseOperation.CLEAN_INSERT));
   }

   @BeforeMethod
   public void setUp() throws Exception
   {
      MockitoAnnotations.initMocks(this);
      ResourceUtils resourceUtils = new ResourceUtils();
      resourceUtils.create(); //postConstruct
      TransUnitTransformer transUnitTransformer = SeamAutowire.instance().use("resourceUtils", resourceUtils).autowire(TransUnitTransformer.class);
      // @formatter:off
      TextFlowSearchService textFlowSearchServiceImpl = SeamAutowire.instance()
            .use("localeServiceImpl", localeService)
            .use("documentDAO", new DocumentDAO(getSession()))
            .use("projectIterationDAO", new ProjectIterationDAO(getSession()))
            .use("entityManager", new FullTextEntityManagerImpl(getEm()))
            .use("session", new FullTextSessionImpl(getSession()))
            .autowire(TextFlowSearchServiceImpl.class);
      handler = SeamAutowire.instance()
            .use("identity", identity)
            .use("localeServiceImpl", localeService)
            .use("textFlowDAO", new TextFlowDAO(getSession()))
            .use("transUnitTransformer", transUnitTransformer)
            .use("textFlowSearchServiceImpl", textFlowSearchServiceImpl)
            .autowire(GetTransUnitListHandler.class);
      // @formatter:on

      jaHLocale = getEm().find(HLocale.class, 3L);
   }

   private void prepareActionAndMockLocaleService(GetTransUnitList action)
   {
      action.setEditorClientId(new EditorClientId("sessionId", 1));
      action.setWorkspaceId(TestFixture.workspaceId(localeId, "plurals", "master"));
      ProjectIterationId projectIterationId = action.getWorkspaceId().getProjectIterationId();
      when(localeService.validateLocaleByProjectIteration(action.getWorkspaceId().getLocaleId(), projectIterationId.getProjectSlug(), projectIterationId.getIterationSlug())).thenReturn(jaHLocale);
      when(localeService.getByLocaleId(localeId)).thenReturn(jaHLocale);
   }

   @Test
   public void testExecuteToGetAll() throws Exception
   {
      GetTransUnitList action = GetTransUnitList.newAction(new GetTransUnitActionContext(documentId));
      prepareActionAndMockLocaleService(action);

      long startTime = System.nanoTime();
      GetTransUnitListResult result = handler.execute(action, null);
      log.info("********** duration :{} second", (System.nanoTime() - startTime) / 1000000000.0);

      log.info("result: {}", result);
      assertThat(result.getDocumentId(), Matchers.equalTo(documentId));
      assertThat(result.getGotoRow(), Matchers.equalTo(0));
      assertThat(TestFixture.asIds(result.getUnits()), Matchers.contains(1, 2, 3, 4, 5));
   }


   @Test
   public void testExecuteToGetByStatus() throws Exception
   {
      GetTransUnitList action = GetTransUnitList.newAction(new GetTransUnitActionContext(documentId).changeFilterNeedReview(true).changeFilterUntranslated(true));
      prepareActionAndMockLocaleService(action);

      GetTransUnitListResult result = handler.execute(action, null);

      log.info("result: {}", result);
      assertThat(result.getDocumentId(), Matchers.equalTo(documentId));
      assertThat(result.getGotoRow(), Matchers.equalTo(0));
      assertThat(TestFixture.asIds(result.getUnits()), Matchers.contains(3, 5, 6, 7, 8));
   }


   @Test
   public void testExecuteForSearch() throws Exception
   {
      // Given: we want to search for file in fuzzy and untranslated text flows
      GetTransUnitList action = GetTransUnitList.newAction(new GetTransUnitActionContext(documentId)
            .changeFindMessage("file").changeFilterUntranslated(true).changeFilterNeedReview(true));
      prepareActionAndMockLocaleService(action);

      // When:
      GetTransUnitListResult result = handler.execute(action, null);

      // Then:
      log.info("result: {}", result);
      assertThat(result.getDocumentId(), Matchers.equalTo(documentId));
      assertThat(result.getGotoRow(), Matchers.equalTo(0));
      assertThat(TestFixture.asIds(result.getUnits()), Matchers.contains(3, 5, 6));
   }


   @Test
   public void selectDocument()
   {
      HDocument hDocument = getEm().find(HDocument.class, 1L);
   }
}
