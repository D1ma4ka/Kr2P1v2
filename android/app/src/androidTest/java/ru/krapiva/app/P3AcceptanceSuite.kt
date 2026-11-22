package ru.krapiva.app

import org.junit.runner.RunWith
import org.junit.runners.Suite
import ru.krapiva.app.account.AccountIntegrationTest
import ru.krapiva.app.account.AccountLayoutTest
import ru.krapiva.app.account.AuthFormTest
import ru.krapiva.app.designsystem.BeerHeroTest

/** Account flow runs last, leaving its real session for the host cold-start check. */
@RunWith(Suite::class)
@Suite.SuiteClasses(AuthFormTest::class, AccountLayoutTest::class, ru.krapiva.app.account.AuthLayoutRegressionTest::class, BeerHeroTest::class, ru.krapiva.app.catalog.CatalogRetentionTest::class, ru.krapiva.app.catalog.CatalogIntegrationTest::class, ru.krapiva.app.diary.DiaryRetentionTest::class, ru.krapiva.app.diary.DiaryIntegrationTest::class, ru.krapiva.app.social.SocialIntegrationTest::class, ru.krapiva.app.social.SocialRetentionTest::class,
    ru.krapiva.app.menu.MenuComparisonLayoutTest::class, ru.krapiva.app.menu.MenuPresetStorageTest::class,
    AccountIntegrationTest::class)
class P3AcceptanceSuite
