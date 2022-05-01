package com.binance.bot.util;


import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.sqlite.SQLiteException;

import javax.sql.DataSource;

public class CreateCryptobotDB {
  public static void createCryptobotDB(DataSource dataSource) {
    dropTables(dataSource);
    Resource resource = new ClassPathResource("/cryptobot_schema.sql");
    ResourceDatabasePopulator databasePopulator = new ResourceDatabasePopulator(resource);
    databasePopulator.execute(dataSource);
  }

  private static void dropTables(DataSource dataSource) {
    Resource resource = new ClassPathResource("/droptables.sql");
    ResourceDatabasePopulator databasePopulator = new ResourceDatabasePopulator(resource);
    try {
      databasePopulator.execute(dataSource);
    } catch (ScriptStatementFailedException ex) {}
  }
}
