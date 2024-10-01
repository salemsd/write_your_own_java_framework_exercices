package com.github.forax.framework.orm;

import javax.sql.DataSource;
import java.beans.PropertyDescriptor;
import java.io.Serial;
import java.lang.reflect.ParameterizedType;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class ORM {

  private ORM() {
    throw new AssertionError();
  }

  @FunctionalInterface
  public interface TransactionBlock {
    void run() throws SQLException;
  }

  private static final Map<Class<?>, String> TYPE_MAPPING = Map.of(
      int.class, "INTEGER",
      Integer.class, "INTEGER",
      long.class, "BIGINT",
      Long.class, "BIGINT",
      String.class, "VARCHAR(255)"
  );

  private static Class<?> findBeanTypeFromRepository(Class<?> repositoryType) {
    var repositorySupertype = Arrays.stream(repositoryType.getGenericInterfaces())
        .flatMap(superInterface -> {
          if (superInterface instanceof ParameterizedType parameterizedType
              && parameterizedType.getRawType() == Repository.class) {
            return Stream.of(parameterizedType);
          }
          return null;
        })
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("invalid repository interface " + repositoryType.getName()));
    var typeArgument = repositorySupertype.getActualTypeArguments()[0];
    if (typeArgument instanceof Class<?> beanType) {
      return beanType;
    }
    throw new IllegalArgumentException("invalid type argument " + typeArgument + " for repository interface " + repositoryType.getName());
  }

  private static class UncheckedSQLException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 42L;

    private UncheckedSQLException(SQLException cause) {
      super(cause);
    }

    @Override
    public SQLException getCause() {
      return (SQLException) super.getCause();
    }
  }


  // --- do not change the code above

  private static final ThreadLocal<Connection> CONNECTION_LOCAL = new ThreadLocal<>();

  public static void transaction(DataSource dataSource, TransactionBlock block) throws SQLException {
    Objects.requireNonNull(dataSource);
    Objects.requireNonNull(block);
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      CONNECTION_LOCAL.set(connection);

      try {
        block.run();
      } catch (Exception e) {
        connection.rollback();
        throw e; // throw it back
      }
      finally { // because we want it to happen no matter what happens
        CONNECTION_LOCAL.remove();
      }

      connection.commit();
    }
  }

  static Connection currentConnection() {
    var connection = CONNECTION_LOCAL.get();
    if (connection == null) {
      throw new IllegalStateException("no transactions available");
    }

    return connection;
  }

  static String findColumnName(PropertyDescriptor property) {
    var getter = property.getReadMethod();
    var column = getter.getAnnotation(Column.class);
    var name = column == null? property.getName(): column.value();
    return name.toUpperCase(Locale.ROOT);
  }

  static String findTableName(Class<?> beanClass) {
    if (beanClass.isAnnotationPresent(Table.class)) {
      return beanClass.getAnnotation(Table.class).value();
    }

    // getSimpleName returns an empty string if it's an anonymous class
    // Hibernate doesn't work with inner classes
    return beanClass.getSimpleName().toUpperCase(Locale.ROOT);
  }

  private static boolean isPrimaryKey(PropertyDescriptor property) {
    var getter = property.getReadMethod();

    if (getter == null) {
      return false;
    }

    return getter.isAnnotationPresent(Id.class);
  }

  public static final String DEFAULT_TYPE = "VARCHAR(255)";

  public static void createTable(Class<?> beanClass) throws SQLException {
    Objects.requireNonNull(beanClass);

    /* Can simplify this by creating a builder class, that has a toString method to return the query
    and fields to store the primary key, columns, ..
     */
    var beanInfo = Utils.beanInfo(beanClass);
    var properties = beanInfo.getPropertyDescriptors();

    var builder = new StringBuilder();
    builder.append("CREATE TABLE ").append(findTableName(beanClass)).append(" (\n");
    var separator = "";
    String primaryColumn = null;
    for (var property : properties) {
      if (property.getName().equals("class")) {
        continue;
      }
      var column = findColumnName(property);
      if (isPrimaryKey(property)) {
        primaryColumn = column;
      }


      var propertyType = property.getPropertyType();
      String notNull = "";
      if (propertyType.isPrimitive()) {
        notNull = "NOT NULL";
      }

      var type = TYPE_MAPPING.getOrDefault(propertyType, DEFAULT_TYPE);

      builder.append(separator).append(column).append(" ").append(type).append(" ").append(notNull);
      separator = ", \n";
    }

    if (primaryColumn != null) {
      builder.append(separator).append("PRIMARY KEY (").append(primaryColumn).append(")");
    }
    builder.append(")");

    // Stream: not a good idea with builder)
//    Arrays.stream(properties)
//            .filter(property -> !property.getName().equals("class"))
//            .forEach(property -> {
//              var column = findColumnName(property);
//              var type = TYPE_MAPPING.getOrDefault(property.getPropertyType(), DEFAULT_TYPE);
//              builder.append(separator).append(column).append(',').append(type);
//              separator = ",\n";
//            });

    var query = builder.toString();
    var connection = currentConnection();
    // escape stuff ??
    try (var statement = connection.createStatement()) {
      statement.executeUpdate(query);
    }

    connection.commit(); // Not needed because create table are auto commit
  }
}
