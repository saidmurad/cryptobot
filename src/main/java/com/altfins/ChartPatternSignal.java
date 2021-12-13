// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: chart_pattern_signal.proto

package com.altfins;

/**
 * Protobuf type {@code altfins.ChartPatternSignal}
 */
public  final class ChartPatternSignal extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:altfins.ChartPatternSignal)
    ChartPatternSignalOrBuilder {
private static final long serialVersionUID = 0L;
  // Use ChartPatternSignal.newBuilder() to construct.
  private ChartPatternSignal(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private ChartPatternSignal() {
    coinPair_ = "";
    pattern_ = "";
    isBreakout_ = false;
    timeFrame_ = 0;
    breakoutPrice_ = 0D;
    targetPrice_ = 0D;
    targetTimeSecs_ = 0L;
    tradeType_ = 0;
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private ChartPatternSignal(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new java.lang.NullPointerException();
    }
    int mutable_bitField0_ = 0;
    com.google.protobuf.UnknownFieldSet.Builder unknownFields =
        com.google.protobuf.UnknownFieldSet.newBuilder();
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          case 10: {
            java.lang.String s = input.readStringRequireUtf8();

            coinPair_ = s;
            break;
          }
          case 18: {
            java.lang.String s = input.readStringRequireUtf8();

            pattern_ = s;
            break;
          }
          case 24: {

            isBreakout_ = input.readBool();
            break;
          }
          case 32: {
            int rawValue = input.readEnum();

            timeFrame_ = rawValue;
            break;
          }
          case 41: {

            breakoutPrice_ = input.readDouble();
            break;
          }
          case 49: {

            targetPrice_ = input.readDouble();
            break;
          }
          case 56: {

            targetTimeSecs_ = input.readInt64();
            break;
          }
          case 64: {
            int rawValue = input.readEnum();

            tradeType_ = rawValue;
            break;
          }
          default: {
            if (!parseUnknownFieldProto3(
                input, unknownFields, extensionRegistry, tag)) {
              done = true;
            }
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(
          e).setUnfinishedMessage(this);
    } finally {
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return com.altfins.ChartPatternSignalOuterClass.internal_static_altfins_ChartPatternSignal_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.altfins.ChartPatternSignalOuterClass.internal_static_altfins_ChartPatternSignal_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.altfins.ChartPatternSignal.class, com.altfins.ChartPatternSignal.Builder.class);
  }

  public static final int COIN_PAIR_FIELD_NUMBER = 1;
  private volatile java.lang.Object coinPair_;
  /**
   * <code>string coin_pair = 1;</code>
   */
  public java.lang.String getCoinPair() {
    java.lang.Object ref = coinPair_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      coinPair_ = s;
      return s;
    }
  }
  /**
   * <code>string coin_pair = 1;</code>
   */
  public com.google.protobuf.ByteString
      getCoinPairBytes() {
    java.lang.Object ref = coinPair_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      coinPair_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int PATTERN_FIELD_NUMBER = 2;
  private volatile java.lang.Object pattern_;
  /**
   * <code>string pattern = 2;</code>
   */
  public java.lang.String getPattern() {
    java.lang.Object ref = pattern_;
    if (ref instanceof java.lang.String) {
      return (java.lang.String) ref;
    } else {
      com.google.protobuf.ByteString bs = 
          (com.google.protobuf.ByteString) ref;
      java.lang.String s = bs.toStringUtf8();
      pattern_ = s;
      return s;
    }
  }
  /**
   * <code>string pattern = 2;</code>
   */
  public com.google.protobuf.ByteString
      getPatternBytes() {
    java.lang.Object ref = pattern_;
    if (ref instanceof java.lang.String) {
      com.google.protobuf.ByteString b = 
          com.google.protobuf.ByteString.copyFromUtf8(
              (java.lang.String) ref);
      pattern_ = b;
      return b;
    } else {
      return (com.google.protobuf.ByteString) ref;
    }
  }

  public static final int IS_BREAKOUT_FIELD_NUMBER = 3;
  private boolean isBreakout_;
  /**
   * <pre>
   * If false, it is an emerging signal.
   * </pre>
   *
   * <code>bool is_breakout = 3;</code>
   */
  public boolean getIsBreakout() {
    return isBreakout_;
  }

  public static final int TIME_FRAME_FIELD_NUMBER = 4;
  private int timeFrame_;
  /**
   * <code>.altfins.TimeFrame time_frame = 4;</code>
   */
  public int getTimeFrameValue() {
    return timeFrame_;
  }
  /**
   * <code>.altfins.TimeFrame time_frame = 4;</code>
   */
  public com.altfins.TimeFrame getTimeFrame() {
    @SuppressWarnings("deprecation")
    com.altfins.TimeFrame result = com.altfins.TimeFrame.valueOf(timeFrame_);
    return result == null ? com.altfins.TimeFrame.UNRECOGNIZED : result;
  }

  public static final int BREAKOUT_PRICE_FIELD_NUMBER = 5;
  private double breakoutPrice_;
  /**
   * <pre>
   * The price at breakout, avaialble in some types of patterns such as breaking resistance and support.
   * </pre>
   *
   * <code>double breakout_price = 5;</code>
   */
  public double getBreakoutPrice() {
    return breakoutPrice_;
  }

  public static final int TARGET_PRICE_FIELD_NUMBER = 6;
  private double targetPrice_;
  /**
   * <code>double target_price = 6;</code>
   */
  public double getTargetPrice() {
    return targetPrice_;
  }

  public static final int TARGET_TIME_SECS_FIELD_NUMBER = 7;
  private long targetTimeSecs_;
  /**
   * <code>int64 target_time_secs = 7;</code>
   */
  public long getTargetTimeSecs() {
    return targetTimeSecs_;
  }

  public static final int TRADE_TYPE_FIELD_NUMBER = 8;
  private int tradeType_;
  /**
   * <code>.altfins.TradeType trade_type = 8;</code>
   */
  public int getTradeTypeValue() {
    return tradeType_;
  }
  /**
   * <code>.altfins.TradeType trade_type = 8;</code>
   */
  public com.altfins.TradeType getTradeType() {
    @SuppressWarnings("deprecation")
    com.altfins.TradeType result = com.altfins.TradeType.valueOf(tradeType_);
    return result == null ? com.altfins.TradeType.UNRECOGNIZED : result;
  }

  private byte memoizedIsInitialized = -1;
  @java.lang.Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    if (!getCoinPairBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 1, coinPair_);
    }
    if (!getPatternBytes().isEmpty()) {
      com.google.protobuf.GeneratedMessageV3.writeString(output, 2, pattern_);
    }
    if (isBreakout_ != false) {
      output.writeBool(3, isBreakout_);
    }
    if (timeFrame_ != com.altfins.TimeFrame.FIFTEEN_MIN.getNumber()) {
      output.writeEnum(4, timeFrame_);
    }
    if (breakoutPrice_ != 0D) {
      output.writeDouble(5, breakoutPrice_);
    }
    if (targetPrice_ != 0D) {
      output.writeDouble(6, targetPrice_);
    }
    if (targetTimeSecs_ != 0L) {
      output.writeInt64(7, targetTimeSecs_);
    }
    if (tradeType_ != com.altfins.TradeType.BUY.getNumber()) {
      output.writeEnum(8, tradeType_);
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (!getCoinPairBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, coinPair_);
    }
    if (!getPatternBytes().isEmpty()) {
      size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, pattern_);
    }
    if (isBreakout_ != false) {
      size += com.google.protobuf.CodedOutputStream
        .computeBoolSize(3, isBreakout_);
    }
    if (timeFrame_ != com.altfins.TimeFrame.FIFTEEN_MIN.getNumber()) {
      size += com.google.protobuf.CodedOutputStream
        .computeEnumSize(4, timeFrame_);
    }
    if (breakoutPrice_ != 0D) {
      size += com.google.protobuf.CodedOutputStream
        .computeDoubleSize(5, breakoutPrice_);
    }
    if (targetPrice_ != 0D) {
      size += com.google.protobuf.CodedOutputStream
        .computeDoubleSize(6, targetPrice_);
    }
    if (targetTimeSecs_ != 0L) {
      size += com.google.protobuf.CodedOutputStream
        .computeInt64Size(7, targetTimeSecs_);
    }
    if (tradeType_ != com.altfins.TradeType.BUY.getNumber()) {
      size += com.google.protobuf.CodedOutputStream
        .computeEnumSize(8, tradeType_);
    }
    size += unknownFields.getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
     return true;
    }
    if (!(obj instanceof com.altfins.ChartPatternSignal)) {
      return super.equals(obj);
    }
    com.altfins.ChartPatternSignal other = (com.altfins.ChartPatternSignal) obj;

    boolean result = true;
    result = result && getCoinPair()
        .equals(other.getCoinPair());
    result = result && getPattern()
        .equals(other.getPattern());
    result = result && (getIsBreakout()
        == other.getIsBreakout());
    result = result && timeFrame_ == other.timeFrame_;
    result = result && (
        java.lang.Double.doubleToLongBits(getBreakoutPrice())
        == java.lang.Double.doubleToLongBits(
            other.getBreakoutPrice()));
    result = result && (
        java.lang.Double.doubleToLongBits(getTargetPrice())
        == java.lang.Double.doubleToLongBits(
            other.getTargetPrice()));
    result = result && (getTargetTimeSecs()
        == other.getTargetTimeSecs());
    result = result && tradeType_ == other.tradeType_;
    result = result && unknownFields.equals(other.unknownFields);
    return result;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    hash = (37 * hash) + COIN_PAIR_FIELD_NUMBER;
    hash = (53 * hash) + getCoinPair().hashCode();
    hash = (37 * hash) + PATTERN_FIELD_NUMBER;
    hash = (53 * hash) + getPattern().hashCode();
    hash = (37 * hash) + IS_BREAKOUT_FIELD_NUMBER;
    hash = (53 * hash) + com.google.protobuf.Internal.hashBoolean(
        getIsBreakout());
    hash = (37 * hash) + TIME_FRAME_FIELD_NUMBER;
    hash = (53 * hash) + timeFrame_;
    hash = (37 * hash) + BREAKOUT_PRICE_FIELD_NUMBER;
    hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
        java.lang.Double.doubleToLongBits(getBreakoutPrice()));
    hash = (37 * hash) + TARGET_PRICE_FIELD_NUMBER;
    hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
        java.lang.Double.doubleToLongBits(getTargetPrice()));
    hash = (37 * hash) + TARGET_TIME_SECS_FIELD_NUMBER;
    hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
        getTargetTimeSecs());
    hash = (37 * hash) + TRADE_TYPE_FIELD_NUMBER;
    hash = (53 * hash) + tradeType_;
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.altfins.ChartPatternSignal parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.altfins.ChartPatternSignal parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.altfins.ChartPatternSignal parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.altfins.ChartPatternSignal parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.altfins.ChartPatternSignal parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static com.altfins.ChartPatternSignal parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static com.altfins.ChartPatternSignal parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.altfins.ChartPatternSignal parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.altfins.ChartPatternSignal parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static com.altfins.ChartPatternSignal parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static com.altfins.ChartPatternSignal parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static com.altfins.ChartPatternSignal parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(com.altfins.ChartPatternSignal prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * Protobuf type {@code altfins.ChartPatternSignal}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:altfins.ChartPatternSignal)
      com.altfins.ChartPatternSignalOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return com.altfins.ChartPatternSignalOuterClass.internal_static_altfins_ChartPatternSignal_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.altfins.ChartPatternSignalOuterClass.internal_static_altfins_ChartPatternSignal_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.altfins.ChartPatternSignal.class, com.altfins.ChartPatternSignal.Builder.class);
    }

    // Construct using com.altfins.ChartPatternSignal.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3
              .alwaysUseFieldBuilders) {
      }
    }
    @java.lang.Override
    public Builder clear() {
      super.clear();
      coinPair_ = "";

      pattern_ = "";

      isBreakout_ = false;

      timeFrame_ = 0;

      breakoutPrice_ = 0D;

      targetPrice_ = 0D;

      targetTimeSecs_ = 0L;

      tradeType_ = 0;

      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return com.altfins.ChartPatternSignalOuterClass.internal_static_altfins_ChartPatternSignal_descriptor;
    }

    @java.lang.Override
    public com.altfins.ChartPatternSignal getDefaultInstanceForType() {
      return com.altfins.ChartPatternSignal.getDefaultInstance();
    }

    @java.lang.Override
    public com.altfins.ChartPatternSignal build() {
      com.altfins.ChartPatternSignal result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.altfins.ChartPatternSignal buildPartial() {
      com.altfins.ChartPatternSignal result = new com.altfins.ChartPatternSignal(this);
      result.coinPair_ = coinPair_;
      result.pattern_ = pattern_;
      result.isBreakout_ = isBreakout_;
      result.timeFrame_ = timeFrame_;
      result.breakoutPrice_ = breakoutPrice_;
      result.targetPrice_ = targetPrice_;
      result.targetTimeSecs_ = targetTimeSecs_;
      result.tradeType_ = tradeType_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder clone() {
      return (Builder) super.clone();
    }
    @java.lang.Override
    public Builder setField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return (Builder) super.setField(field, value);
    }
    @java.lang.Override
    public Builder clearField(
        com.google.protobuf.Descriptors.FieldDescriptor field) {
      return (Builder) super.clearField(field);
    }
    @java.lang.Override
    public Builder clearOneof(
        com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return (Builder) super.clearOneof(oneof);
    }
    @java.lang.Override
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        int index, java.lang.Object value) {
      return (Builder) super.setRepeatedField(field, index, value);
    }
    @java.lang.Override
    public Builder addRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return (Builder) super.addRepeatedField(field, value);
    }
    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.altfins.ChartPatternSignal) {
        return mergeFrom((com.altfins.ChartPatternSignal)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.altfins.ChartPatternSignal other) {
      if (other == com.altfins.ChartPatternSignal.getDefaultInstance()) return this;
      if (!other.getCoinPair().isEmpty()) {
        coinPair_ = other.coinPair_;
        onChanged();
      }
      if (!other.getPattern().isEmpty()) {
        pattern_ = other.pattern_;
        onChanged();
      }
      if (other.getIsBreakout() != false) {
        setIsBreakout(other.getIsBreakout());
      }
      if (other.timeFrame_ != 0) {
        setTimeFrameValue(other.getTimeFrameValue());
      }
      if (other.getBreakoutPrice() != 0D) {
        setBreakoutPrice(other.getBreakoutPrice());
      }
      if (other.getTargetPrice() != 0D) {
        setTargetPrice(other.getTargetPrice());
      }
      if (other.getTargetTimeSecs() != 0L) {
        setTargetTimeSecs(other.getTargetTimeSecs());
      }
      if (other.tradeType_ != 0) {
        setTradeTypeValue(other.getTradeTypeValue());
      }
      this.mergeUnknownFields(other.unknownFields);
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() {
      return true;
    }

    @java.lang.Override
    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      com.altfins.ChartPatternSignal parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (com.altfins.ChartPatternSignal) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }

    private java.lang.Object coinPair_ = "";
    /**
     * <code>string coin_pair = 1;</code>
     */
    public java.lang.String getCoinPair() {
      java.lang.Object ref = coinPair_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        coinPair_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>string coin_pair = 1;</code>
     */
    public com.google.protobuf.ByteString
        getCoinPairBytes() {
      java.lang.Object ref = coinPair_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        coinPair_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>string coin_pair = 1;</code>
     */
    public Builder setCoinPair(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      coinPair_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>string coin_pair = 1;</code>
     */
    public Builder clearCoinPair() {
      
      coinPair_ = getDefaultInstance().getCoinPair();
      onChanged();
      return this;
    }
    /**
     * <code>string coin_pair = 1;</code>
     */
    public Builder setCoinPairBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      coinPair_ = value;
      onChanged();
      return this;
    }

    private java.lang.Object pattern_ = "";
    /**
     * <code>string pattern = 2;</code>
     */
    public java.lang.String getPattern() {
      java.lang.Object ref = pattern_;
      if (!(ref instanceof java.lang.String)) {
        com.google.protobuf.ByteString bs =
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        pattern_ = s;
        return s;
      } else {
        return (java.lang.String) ref;
      }
    }
    /**
     * <code>string pattern = 2;</code>
     */
    public com.google.protobuf.ByteString
        getPatternBytes() {
      java.lang.Object ref = pattern_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        pattern_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }
    /**
     * <code>string pattern = 2;</code>
     */
    public Builder setPattern(
        java.lang.String value) {
      if (value == null) {
    throw new NullPointerException();
  }
  
      pattern_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>string pattern = 2;</code>
     */
    public Builder clearPattern() {
      
      pattern_ = getDefaultInstance().getPattern();
      onChanged();
      return this;
    }
    /**
     * <code>string pattern = 2;</code>
     */
    public Builder setPatternBytes(
        com.google.protobuf.ByteString value) {
      if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
      
      pattern_ = value;
      onChanged();
      return this;
    }

    private boolean isBreakout_ ;
    /**
     * <pre>
     * If false, it is an emerging signal.
     * </pre>
     *
     * <code>bool is_breakout = 3;</code>
     */
    public boolean getIsBreakout() {
      return isBreakout_;
    }
    /**
     * <pre>
     * If false, it is an emerging signal.
     * </pre>
     *
     * <code>bool is_breakout = 3;</code>
     */
    public Builder setIsBreakout(boolean value) {
      
      isBreakout_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * If false, it is an emerging signal.
     * </pre>
     *
     * <code>bool is_breakout = 3;</code>
     */
    public Builder clearIsBreakout() {
      
      isBreakout_ = false;
      onChanged();
      return this;
    }

    private int timeFrame_ = 0;
    /**
     * <code>.altfins.TimeFrame time_frame = 4;</code>
     */
    public int getTimeFrameValue() {
      return timeFrame_;
    }
    /**
     * <code>.altfins.TimeFrame time_frame = 4;</code>
     */
    public Builder setTimeFrameValue(int value) {
      timeFrame_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>.altfins.TimeFrame time_frame = 4;</code>
     */
    public com.altfins.TimeFrame getTimeFrame() {
      @SuppressWarnings("deprecation")
      com.altfins.TimeFrame result = com.altfins.TimeFrame.valueOf(timeFrame_);
      return result == null ? com.altfins.TimeFrame.UNRECOGNIZED : result;
    }
    /**
     * <code>.altfins.TimeFrame time_frame = 4;</code>
     */
    public Builder setTimeFrame(com.altfins.TimeFrame value) {
      if (value == null) {
        throw new NullPointerException();
      }
      
      timeFrame_ = value.getNumber();
      onChanged();
      return this;
    }
    /**
     * <code>.altfins.TimeFrame time_frame = 4;</code>
     */
    public Builder clearTimeFrame() {
      
      timeFrame_ = 0;
      onChanged();
      return this;
    }

    private double breakoutPrice_ ;
    /**
     * <pre>
     * The price at breakout, avaialble in some types of patterns such as breaking resistance and support.
     * </pre>
     *
     * <code>double breakout_price = 5;</code>
     */
    public double getBreakoutPrice() {
      return breakoutPrice_;
    }
    /**
     * <pre>
     * The price at breakout, avaialble in some types of patterns such as breaking resistance and support.
     * </pre>
     *
     * <code>double breakout_price = 5;</code>
     */
    public Builder setBreakoutPrice(double value) {
      
      breakoutPrice_ = value;
      onChanged();
      return this;
    }
    /**
     * <pre>
     * The price at breakout, avaialble in some types of patterns such as breaking resistance and support.
     * </pre>
     *
     * <code>double breakout_price = 5;</code>
     */
    public Builder clearBreakoutPrice() {
      
      breakoutPrice_ = 0D;
      onChanged();
      return this;
    }

    private double targetPrice_ ;
    /**
     * <code>double target_price = 6;</code>
     */
    public double getTargetPrice() {
      return targetPrice_;
    }
    /**
     * <code>double target_price = 6;</code>
     */
    public Builder setTargetPrice(double value) {
      
      targetPrice_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>double target_price = 6;</code>
     */
    public Builder clearTargetPrice() {
      
      targetPrice_ = 0D;
      onChanged();
      return this;
    }

    private long targetTimeSecs_ ;
    /**
     * <code>int64 target_time_secs = 7;</code>
     */
    public long getTargetTimeSecs() {
      return targetTimeSecs_;
    }
    /**
     * <code>int64 target_time_secs = 7;</code>
     */
    public Builder setTargetTimeSecs(long value) {
      
      targetTimeSecs_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>int64 target_time_secs = 7;</code>
     */
    public Builder clearTargetTimeSecs() {
      
      targetTimeSecs_ = 0L;
      onChanged();
      return this;
    }

    private int tradeType_ = 0;
    /**
     * <code>.altfins.TradeType trade_type = 8;</code>
     */
    public int getTradeTypeValue() {
      return tradeType_;
    }
    /**
     * <code>.altfins.TradeType trade_type = 8;</code>
     */
    public Builder setTradeTypeValue(int value) {
      tradeType_ = value;
      onChanged();
      return this;
    }
    /**
     * <code>.altfins.TradeType trade_type = 8;</code>
     */
    public com.altfins.TradeType getTradeType() {
      @SuppressWarnings("deprecation")
      com.altfins.TradeType result = com.altfins.TradeType.valueOf(tradeType_);
      return result == null ? com.altfins.TradeType.UNRECOGNIZED : result;
    }
    /**
     * <code>.altfins.TradeType trade_type = 8;</code>
     */
    public Builder setTradeType(com.altfins.TradeType value) {
      if (value == null) {
        throw new NullPointerException();
      }
      
      tradeType_ = value.getNumber();
      onChanged();
      return this;
    }
    /**
     * <code>.altfins.TradeType trade_type = 8;</code>
     */
    public Builder clearTradeType() {
      
      tradeType_ = 0;
      onChanged();
      return this;
    }
    @java.lang.Override
    public final Builder setUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFieldsProto3(unknownFields);
    }

    @java.lang.Override
    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }


    // @@protoc_insertion_point(builder_scope:altfins.ChartPatternSignal)
  }

  // @@protoc_insertion_point(class_scope:altfins.ChartPatternSignal)
  private static final com.altfins.ChartPatternSignal DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new com.altfins.ChartPatternSignal();
  }

  public static com.altfins.ChartPatternSignal getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<ChartPatternSignal>
      PARSER = new com.google.protobuf.AbstractParser<ChartPatternSignal>() {
    @java.lang.Override
    public ChartPatternSignal parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new ChartPatternSignal(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<ChartPatternSignal> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<ChartPatternSignal> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.altfins.ChartPatternSignal getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

