const { TypeObj, ValueObj } = require("sigmastate-js/main");

function testRange(factory, min, max) {
  expect(factory(max).data).toEqual(max);
  expect(() => factory(max + 1).data).toThrow();
  expect(factory(-1).data).toEqual(-1);
  expect(factory(min).data).toEqual(min);
  expect(() => factory(min - 1).data).toThrow();
}

describe("Smoke tests for Values", () => {
  it("Should create values of primitive types", () => {
    expect(ValueObj.ofByte(0).data).toEqual(0);
    expect(ValueObj.ofByte(0).tpe).toEqual(TypeObj.Byte);
    testRange(function(v) { return ValueObj.ofByte(v); }, -128, 127);
    testRange(function(v) { return ValueObj.ofShort(v); }, -32768, 32767);
    testRange(function(v) { return ValueObj.ofInt(v); }, -0x7FFFFFFF - 1, 0x7FFFFFFF);
    testRange(function(v) { return ValueObj.ofLong(v); }, -0x8000000000000000n, 0x7fffffffffffffffn);
  });

  it("Should create values of complex types", () => {
    let pair = ValueObj.pairOf(ValueObj.ofByte(10), ValueObj.ofLong(20n));
    expect(pair.data).toEqual([10, 20n]);
    expect(pair.tpe.name).toEqual("(Byte, Long)");

    let coll = ValueObj.collOf([-10, 0, 10], TypeObj.Byte)
    expect(coll.tpe.name).toEqual("Coll[Byte]");
  });

  let unitHex = "62";
  let booleanHex = "0101";
  let byteHex = "027f";
  let shortHex = "03feff03";
  let intHex = "04feffffffffffffffff01";
  let longHex = "05e012";
  let bigIntHex = "060900fffffffffffffffe";
  let groupElementHex = "0702c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5";
  let sigmaPropHex = "08cd0297c44a12f4eb99a85d298fa3ba829b5b42b9f63798c980ece801cc663cc5fc9e";
  let avlTreeHex = "643100d2e101ff01fc047c7f6f00ff80129df69a5090012f01ffca99f5bfff0c803601800100";
  let boxHex = "63b96000d1968302010100ff83020193040204020100c0843d000401010e32297000800b80f1d56c809a8c6affbed864b87f007f6f007f00ac00018c01c4fdff011088807f0100657f00f9ab0101ff6d6505a4a7b5a2e7a4a4dd3a05feffffffffffffffff01003bd5c630803cfff6c1ff7f7fb980ff136afc011f8080b8b04ad4dbda2d7f4e01";
  let collHex = "1a0203010203020a14";
  let pairHex = "3e050a28"

  it("Unit Value.toHex", () => {
    let v = ValueObj.fromHex(unitHex)
    expect(v.toHex()).toEqual(unitHex)
  });

  it("Boolean Value.toHex", () => {
    let v = ValueObj.fromHex(booleanHex)
    expect(v.toHex()).toEqual(booleanHex)
  });

  it("Byte Value.toHex", () => {
    let v = ValueObj.fromHex(byteHex)
    expect(v.toHex()).toEqual(byteHex)
  });

  it("Short Value.toHex", () => {
    let v = ValueObj.fromHex(shortHex)
    expect(v.toHex()).toEqual(shortHex)
  });

  it("Int Value.toHex", () => {
    let v = ValueObj.fromHex(intHex)
    expect(v.toHex()).toEqual(intHex)
  });

  it("Long Value.toHex", () => {
    let v = ValueObj.ofLong(1200n)
    expect(v.toHex()).toEqual(longHex)
  });

  it("BigInt Value.toHex", () => {
    let v = ValueObj.ofBigInt(0xfffffffffffffffen)
    expect(v.toHex()).toEqual(bigIntHex)
  });

  it("GroupElement Value.toHex", () => {
    let v = ValueObj.fromHex(groupElementHex)
    expect(v.toHex()).toEqual(groupElementHex)
  });

  it("SigmaProp Value.toHex", () => {
    let v = ValueObj.fromHex(sigmaPropHex)
    expect(v.toHex()).toEqual(sigmaPropHex)
  });

  it("AvlTree Value.toHex", () => {
    let v = ValueObj.fromHex(avlTreeHex)
    expect(v.toHex()).toEqual(avlTreeHex)
  });

  it("Box Value.toHex", () => {
    let v = ValueObj.fromHex(boxHex)
    expect(v.toHex()).toEqual(boxHex)
  });

  it("Coll Value.toHex", () => {
    let arr = [ [1, 2, 3], [10, 20] ]
    let t = TypeObj.collType(TypeObj.Byte)
    let collV = ValueObj.collOf(arr, t)

    expect(collV.tpe.name).toEqual("Coll[Coll[Byte]]");
    expect(collV.toHex()).toEqual(collHex)
  });

  it("Pair Value.toHex", () => {
    let fst = ValueObj.ofByte(10)
    let snd = ValueObj.ofLong(20)
    let pair = ValueObj.pairOf(fst, snd)
    expect(pair.tpe.name).toEqual("(Byte, Long)");
    expect(pair.toHex()).toEqual(pairHex)
  });

  it("Long Value.fromHex", () => {
    let v = ValueObj.fromHex(longHex)
    expect(v.data).toEqual(1200n)
    expect(v.tpe.name).toEqual("Long")
  });

  it("Coll Value.fromHex", () => {
    let coll = ValueObj.fromHex(collHex)
    expect(coll.tpe.name).toEqual("Coll[Coll[Byte]]");
    expect(coll.toHex()).toEqual(collHex)
  });

  it("Pair Value.fromHex", () => {
    let p = ValueObj.fromHex(pairHex)
    expect(p.tpe.name).toEqual("(Byte, Long)");
    expect(p.toHex()).toEqual(pairHex)
  });
});
