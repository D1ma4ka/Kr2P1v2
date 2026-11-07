package ru.krapiva.app

import org.junit.runner.RunWith
import org.junit.runners.Suite
import ru.krapiva.app.account.AccountIntegrationTest
import ru.krapiva.app.account.AccountLayoutTest
import ru.krapiva.app.designsystem.BeerHeroTest

/** Account flow runs last, leaving its real session for the host cold-start check. */
@RunWith(Suite::class)
@Suite.SuiteClasses(AccountLayoutTest::class, BeerHeroTest::class, ru.krapiva.app.catalog.CatalogRetentionTest::class, ru.krapiva.app.catalog.CatalogIntegrationTest::class, ru.krapiva.app.diary.DiaryRetentionTest::class, ru.krapiva.app.diary.DiaryIntegrationTest::class, AccountIntegrationTest::class)
class P3AcceptanceSuite
