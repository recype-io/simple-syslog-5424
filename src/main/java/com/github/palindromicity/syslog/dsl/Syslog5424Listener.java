package com.github.palindromicity.syslog.dsl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.github.palindromicity.syslog.KeyProvider;
import com.github.palindromicity.syslog.NilPolicy;
import com.github.palindromicity.syslog.StructuredDataPolicy;
import com.github.palindromicity.syslog.dsl.generated.Rfc5424BaseListener;
import com.github.palindromicity.syslog.dsl.generated.Rfc5424Listener;
import com.github.palindromicity.syslog.dsl.generated.Rfc5424Parser;
import com.github.palindromicity.syslog.util.Validate;

/**
 * Simple implementation of {@link Rfc5424Listener}.
 * <p>
 * {@code Syslog5424Listener} populates a {@code Map} with the values parsed from a valid RFC 5424 syslog line.
 * Nil ('-') values are handled according the {@link NilPolicy}.
 * </p>
 * <p>
 * The {@code Syslog5424Listener} uses the provided {@link KeyProvider} when inserting items into the map.
 * </p>
 */
public class Syslog5424Listener extends Rfc5424BaseListener {

  private static final String DASH = "-";

  /**
   * {@link KeyProvider} that provides our key names.
   */
  private KeyProvider keyProvider;

  /**
   * {@link NilPolicy} for parsing.
   */
  private NilPolicy nilPolicy = NilPolicy.OMIT;

  /**
   * {@link StructuredDataPolicy} for parsing.
   */
  private StructuredDataPolicy structuredDataPolicy = StructuredDataPolicy.FLATTEN;

  /**
   * The {@code Map} used to store our syslog values.
   */
  private final Map<String, Object> msgMap = new HashMap<>();

  /**
   * Create a new {@code Syslog5424Listener}.
   *
   * @param keyProvider {@link KeyProvider} used for map insertion.
   */
  public Syslog5424Listener(KeyProvider keyProvider) {
    this(keyProvider, null, null);
  }

  /**
   * Create a new {@code Syslog5424Listener}.
   *
   * @param keyProvider {@link KeyProvider} used for map insertion.
   * @param nilPolicy {@link NilPolicy} used for handling nil values.
   * @param structuredDataPolicy {@link StructuredDataPolicy} used for handling Structured Data output.
   */
  public Syslog5424Listener(KeyProvider keyProvider, NilPolicy nilPolicy, StructuredDataPolicy structuredDataPolicy) {
    Validate.notNull(keyProvider, "keyProvider");
    this.keyProvider = keyProvider;
    if (nilPolicy != null) {
      this.nilPolicy = nilPolicy;
    }
    if (structuredDataPolicy != null) {
      this.structuredDataPolicy = structuredDataPolicy;
    }
  }

  /**
   * Returns the {@code Map} of syslog values with the keys as provided by the {@link KeyProvider}.
   * The map returned is unmodifiable.
   *
   * @return unmodifiable {@code Map}
   */
  public Map<String, Object> getMsgMap() {
    return Collections.unmodifiableMap(msgMap);
  }


  @Override
  public void exitHeaderPriorityValue(Rfc5424Parser.HeaderPriorityValueContext ctx) {
    String priority = ctx.getText();
    msgMap.put(keyProvider.getHeaderPriority(), priority);
    int pri = Integer.parseInt(priority);
    int sev = pri % 8;
    int facility = pri / 8;
    msgMap.put(keyProvider.getHeaderSeverity(), String.valueOf(sev));
    msgMap.put(keyProvider.getHeaderFacility(), String.valueOf(facility));
  }

  @Override
  public void exitHeaderVersion(Rfc5424Parser.HeaderVersionContext ctx) {
    msgMap.put(keyProvider.getHeaderVersion(), ctx.getText());
  }

  @Override
  public void exitHeaderHostName(Rfc5424Parser.HeaderHostNameContext ctx) {
    msgMap.put(keyProvider.getHeaderHostName(), ctx.getText());

  }

  @Override
  public void exitHeaderNilHostName(Rfc5424Parser.HeaderNilHostNameContext ctx) {
    if (nilPolicy != NilPolicy.OMIT) {
      handleNil(keyProvider::getHeaderHostName);
    }
  }

  @Override
  public void exitHeaderAppName(Rfc5424Parser.HeaderAppNameContext ctx) {
    msgMap.put(keyProvider.getHeaderAppName(), ctx.getText());

  }

  @Override
  public void exitHeaderNilAppName(Rfc5424Parser.HeaderNilAppNameContext ctx) {
    if (nilPolicy != NilPolicy.OMIT) {
      handleNil(keyProvider::getHeaderAppName);
    }
  }

  @Override
  public void exitHeaderProcId(Rfc5424Parser.HeaderProcIdContext ctx) {
    msgMap.put(keyProvider.getHeaderProcessId(), ctx.getText());

  }

  @Override
  public void exitHeaderNilProcId(Rfc5424Parser.HeaderNilProcIdContext ctx) {
    if (nilPolicy != NilPolicy.OMIT) {
      handleNil(keyProvider::getHeaderProcessId);
    }
  }

  @Override
  public void exitHeaderMsgId(Rfc5424Parser.HeaderMsgIdContext ctx) {
    msgMap.put(keyProvider.getHeaderMessageId(), ctx.getText());
  }

  @Override
  public void exitHeaderNilMsgId(Rfc5424Parser.HeaderNilMsgIdContext ctx) {
    if (nilPolicy != NilPolicy.OMIT) {
      handleNil(keyProvider::getHeaderMessageId);
    }
  }

  @Override
  public void exitHeaderTimeStamp(Rfc5424Parser.HeaderTimeStampContext ctx) {
    msgMap.put(keyProvider.getHeaderTimeStamp(), ctx.full_date().getText()
        + "T" + ctx.full_time().getText());
  }

  @Override
  public void exitHeaderNilTimestamp(Rfc5424Parser.HeaderNilTimestampContext ctx) {
    if (nilPolicy != NilPolicy.OMIT) {
      handleNil(keyProvider::getHeaderTimeStamp);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void exitSdElement(Rfc5424Parser.SdElementContext ctx) {
    String id = ctx.sd_id().getText();
    if (structuredDataPolicy == StructuredDataPolicy.FLATTEN) {
      for (Rfc5424Parser.Sd_paramContext paramContext : ctx.sd_param()) {
        msgMap.put(String.format(keyProvider.getStructuredElementIdParamNameFormat(), (id),
            ((Rfc5424Parser.SdParamContext) paramContext).param_name()
                .getText()),
            ((Rfc5424Parser.SdParamContext) paramContext).param_value().getText());

      }
    } else if (structuredDataPolicy == StructuredDataPolicy.MAP_OF_MAPS) {
      msgMap.putIfAbsent("structuredData", new HashMap<String, Object>());
      Map<String, Object> paramMap = new HashMap<>();
      for (Rfc5424Parser.Sd_paramContext paramContext : ctx.sd_param()) {
        paramMap.put(((Rfc5424Parser.SdParamContext) paramContext).param_name()
                .getText(),
            ((Rfc5424Parser.SdParamContext) paramContext).param_value().getText());
      }
      ((Map<String, Object>) msgMap.get("structuredData")).put(id, paramMap);
    }
  }

  @Override
  public void exitMsg_any(Rfc5424Parser.Msg_anyContext ctx) {
    msgMap.put(keyProvider.getMessage(), ctx.getText().trim());
  }

  @Override
  public void exitMsg_utf8(Rfc5424Parser.Msg_utf8Context ctx) {
    msgMap.put(keyProvider.getMessage(), ctx.getText().trim());
  }

  private void handleNil(Supplier<String> supplier) {
    if (nilPolicy == NilPolicy.DASH) {
      msgMap.put(supplier.get(), DASH);
    } else if (nilPolicy == NilPolicy.NULL) {
      msgMap.put(supplier.get(), null);
    }
  }
}
