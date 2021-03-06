package play.json.extra

import org.scalatest.FunSuite

import play.api.libs.json._
import org.joda.time._

import play.json.extra._
import play.json.extra.tuples._

final case class RecursiveClass(o: Option[RecursiveClass] = None, s: String)

object RecursiveClass {

  import implicits.optionWithNull

  implicit def jsonFormat: InvariantFormat[RecursiveClass] = Jsonx.formatCaseClass[RecursiveClass]
}

sealed trait RecursiveAdt

final case class RecursiveChild(o: Option[RecursiveAdt], s: String) extends RecursiveAdt

object RecursiveFormat {

  import implicits.optionWithNull

  implicit def jsonFormat: Format[RecursiveAdt] = Jsonx.formatAdt[RecursiveAdt](AdtEncoder.TypeAsField)

  implicit def jsonFormat2: InvariantFormat[RecursiveChild] = Jsonx.formatCaseClass[RecursiveChild]
}

object Adt {

  sealed trait SomeAdt

  case object ChoiceA extends SomeAdt

  case object ChoiceB extends SomeAdt

  final case class X(i: Int, s: String) extends SomeAdt

  object X {
    implicit def jsonFormat = Jsonx.formatCaseClass[X]
  }

  final case class Y(i: Int, s: String) extends SomeAdt

  object Y {
    implicit def jsonFormat = Jsonx.formatCaseClass[Y]
  }

}

object AdtWithEmptyLeafs {

  sealed trait SomeAdt

  final case class A() extends SomeAdt

  object A {
    implicit def jsonFormat = Jsonx.formatCaseClass[A]
  }

  final case class B() extends SomeAdt

  object B {
    implicit def jsonFormat = Jsonx.formatCaseClass[B]
  }

}

class PlayJsonExtensionsTest extends FunSuite {

  import implicits.optionWithNull

  test("de/serialize case class > 22") {
    case class Bar(a: Int, b: Float)
    case class Foo(_1: Bar, _2: String, _3: Int, _4: Int, _5: Int, _21: Int, _22: Int, _23: Int, _24: Int, _25: Int, _31: Int, _32: Int, _33: Int, _34: Int, _35: Int, _41: Int, _42: Int, _43: Int, _44: Int, _45: Int, _51: Int, _52: Int, _53: Int, _54: Int, _55: Int)
    val foo = Foo(Bar(5, 1.0f), "sdf", 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5)
    implicit def fmt1 = Jsonx.formatCaseClass[Bar]
    implicit def fmt2 = Jsonx.formatCaseClass[Foo]
    val json = Json.toJson(foo)
    assert(foo === json.as[Foo])
  }
  test("de/serialize empty case class") {
    case class Bar()
    implicit def fmt1 = Jsonx.formatCaseClass[Bar]
    val bar = Bar()
    val json = Json.toJson(bar)
    assert(bar === json.as[Bar])
  }
  test("formatCaseClass with explicit return type") {
    case class Bar()
    implicit def fmt1: Format[Bar] = Jsonx.formatCaseClass[Bar]
    val bar = Bar()
    val json = Json.toJson(bar)
    assert(bar === json.as[Bar])
  }
  test("magically de/serialize case class > 22") {
    import play.json.extra.ImplicitCaseClassFormatDefault.formatCaseClass
    case class Bar(a: Int, b: Float)
    case class Foo(_1: Bar, _2: String, _3: Int, _4: Int, _5: Int, _21: Int, _22: Int, _23: Int, _24: Int, _25: Int, _31: Int, _32: Int, _33: Int, _34: Int, _35: Int, _41: Int, _42: Int, _43: Int, _44: Int, _45: Int, _51: Int, _52: Int, _53: Int, _54: Int, _55: Int)
    val foo = Foo(Bar(5, 1.0f), "sdf", 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5)
    val json = Json.toJson(foo)
    assert(foo === json.as[Foo])
  }

  case class Baz(a: Int)

  case class Bar(a: Int)

  test("magical implicit formatter default with overrides") {
    object formatters extends play.json.extra.ImplicitCaseClassFormatDefault {
      implicit def fmt = new Reads[Bar] {
        def reads(json: JsValue) = JsSuccess(Bar(1))
      }
    }
    import formatters._
    val json = Json.parse( """{"a": 2}""")
    assert(Baz(2) === json.as[Baz])
    assert(Bar(1) === json.as[Bar])
  }
  test("serializing None skips fields") {
    // note, using null for a Scala String doesn't work with play Json
    case class Bar(a: Option[String], b: String, d: Option[String] = None)
    val bar = Bar(Some("foo"), "foo", None)
    implicit def fmt1 = Jsonx.formatCaseClass[Bar]
    val json = Json.parse(Json.stringify(// <- otherwise c = JsString(null), not JsNull
      Json.toJson(bar)
    ))
    //    assert(JsSuccess(bar) === json.validate[Bar])
    assert(
      Set("a" -> JsString("foo"), "b" -> JsString("foo"))
        === json.as[JsObject].fields.toSet
    )
  }
  test("require to JsError") {
    // note, using null for a Scala String doesn't work with play Json
    case class Bar(a: Int) {
      require(a > 5, "a needs to be larger than 5")
    }
    case class Baz(bar: Bar)
    implicit def fmt1 = Jsonx.formatCaseClass[Bar]
    implicit def fmt2 = Jsonx.formatCaseClass[Baz]
    assert(Baz(Bar(6)) === Json.parse( """{"bar":{"a":6}}""").validate[Baz].get)
    val capturedFailedRequire = Json.parse( """{"bar":{"a":5}}""").validate[Baz]
    assert(
      capturedFailedRequire.asInstanceOf[JsError].errors.head._2.head.message contains "requirement failed: a needs to be larger than 5"
    )
    assert(
      capturedFailedRequire.asInstanceOf[JsError].errors.head._1.toString === "/bar"
    )
  }
  test("serialize Adt") {
    import Adt._
    implicit val jsonFormat = Jsonx.formatAdt[SomeAdt](AdtEncoder.TypeAsField)
    val a: SomeAdt = ChoiceA
    val b: SomeAdt = ChoiceB
    val x = X(99, "Chris")
    val y = Y(99, "Chris")
    assert("ChoiceA" === Json.toJson(ChoiceA).as[JsString].value)
    assert("ChoiceB" === Json.toJson(ChoiceB).as[JsString].value)
    assert("ChoiceA" === Json.toJson(a).as[JsString].value)
    assert("ChoiceB" === Json.toJson(b).as[JsString].value)

    assert(x !== y)
    assert(JsSuccess(ChoiceA) === Json.fromJson[SomeAdt](Json.toJson(ChoiceA)))
    assert(JsSuccess(ChoiceB) === Json.fromJson[SomeAdt](Json.toJson(ChoiceB)))
    assert(JsSuccess(x) === Json.fromJson[SomeAdt](Json.toJson[SomeAdt](x)))
    assert(JsSuccess(y) === Json.fromJson[SomeAdt](Json.toJson[SomeAdt](y)))
    assert(JsSuccess(x) === Json.fromJson[SomeAdt](Json.toJson(x)))
    assert(JsSuccess(y) === Json.fromJson[SomeAdt](Json.toJson(y)))
  }
  test("serialize Adt with empty leafs") {
    import AdtWithEmptyLeafs._
    implicit val jsonFormat = Jsonx.formatAdt[SomeAdt](AdtEncoder.TypeAsField)
    val x = A()
    val y = B()
    assert(JsSuccess(x) === Json.fromJson[SomeAdt](Json.toJson[SomeAdt](x)))
    assert(JsSuccess(y) === Json.fromJson[SomeAdt](Json.toJson[SomeAdt](y)))
    assert(JsSuccess(x) === Json.fromJson[SomeAdt](Json.toJson(x)))
    assert(JsSuccess(y) === Json.fromJson[SomeAdt](Json.toJson(y)))
  }
  test("serialize recursive class") {
    import RecursiveFormat._
    val x = RecursiveClass(Some(RecursiveClass(Some(RecursiveClass(None, "c")), "b")), "a")
    val json = Json.toJson[RecursiveClass](x)(implicitly[Format[RecursiveClass]])
    val res = Json.fromJson[RecursiveClass](json)(implicitly[Format[RecursiveClass]])
    assert(JsSuccess(x) === res)
  }
  test("serialize recursive child") {
    import RecursiveFormat._
    val x = RecursiveChild(Some(RecursiveChild(Some(RecursiveChild(None, "c")), "b")), "a")
    val json = Json.toJson[RecursiveChild](x)(implicitly[Format[RecursiveChild]])
    val res = Json.fromJson[RecursiveChild](json)(implicitly[Format[RecursiveChild]])
    assert(JsSuccess(x) === res)
  }
  test("serialize recursive Adt") {
    import RecursiveFormat._
    val x = RecursiveChild(Some(RecursiveChild(Some(RecursiveChild(None, "c")), "b")), "a")
    val json = Json.toJson[RecursiveAdt](x)(implicitly[Format[RecursiveAdt]])
    val res = Json.fromJson[RecursiveAdt](json)(implicitly[Format[RecursiveAdt]])
    assert(JsSuccess(x) === res)
  }
  test("deserialize case class error messages") {
    val json = Json.parse( """{"i":"test"}""")
    val res = Json.fromJson[Adt.X](json)
    res match {
      case JsError(_errors) =>
        val errors = _errors.map { case (k, v) => (k.toString, v) }.toMap
        assert(
          2 === _errors.size
        )
        assert(
          "error.expected.jsnumber" === errors("/i").head.message
        )
        assert(
          "error.path.missing" === errors("/s").head.message
        )
      case _ => assert(false)
    }
  }
  test("deserialize tuple") {
    val json = Json.parse( """[1,1.0,"Test"]""")
    val res = Json.fromJson[(Int,Double,String)](json)
    assert(JsSuccess((1, 1.0, "Test")) === res)
    assert(JsSuccess((1, 1.0, "Test")) === Json.toJson(res.get).validate[(Int, Double, String)])
  }
  test("deserialize tuple wrong size") {
    case class Foo(bar: (Int, Double, String))
    implicit def jsonFoo = Jsonx.formatCaseClass[Foo]
    val json = Json.parse( """{"bar": [1,1.1]}""")
    val res = Json.fromJson[Foo](json)
    res match {
      case JsError(_errors) =>
        val errors = _errors.map { case (k, v) => (k.toString, v) }.toMap
        assert(
          "Expected array of size 3, found: [1,1.1]" === errors("/bar").head.message
        )
      case _ => assert(false)
    }
  }
}


abstract class JsonTestClasses {
  implicit def option[A](implicit reads: Reads[A]): Reads[Option[A]]

  case class A(s: String)

  object A {
    implicit def jsonFormat = Jsonx.formatCaseClass[A]
  }

  case class B(s: Option[String])

  object B {
    implicit def jsonFormat = Jsonx.formatCaseClass[B]
  }

  case class C(i: Int, b: Option[B])

  object C {
    implicit def jsonFormat = Jsonx.formatCaseClass[C]
  }

  case class A2(s: String)

  object A2 {
    implicit def jsonFormat = Json.format[A2]
  }

  case class B2(s: Option[String])

  object B2 {
    implicit def jsonFormat = Json.format[B2]
  }

  case class C2(i: Int, b: Option[B2])

  object C2 {
    implicit def jsonFormat = Json.format[C2]
  }

  case class Mandatory(s: List[String])

  object Mandatory {
    implicit def jsonFormat = Jsonx.formatCaseClass[Mandatory]
  }

  case class Optional(o: Option[Mandatory])

  object Optional {
    implicit def jsonFormat = Jsonx.formatCaseClass[Optional]
  }

  case class Mandatory2(s: List[String])

  object Mandatory2 {
    implicit def jsonFormat = Jsonx.formatCaseClass[Mandatory2]
  }

  case class Optional2(o: Option[Mandatory2])

  object Optional2 {
    implicit def jsonFormat = Jsonx.formatCaseClass[Optional2]
  }

  case class ListInner(string: String)

  object ListInner {
    implicit def jsonFormat = Jsonx.formatCaseClass[ListInner]
  }

  case class ListOuter(inner: List[ListInner])

  object ListOuter {
    implicit def jsonFormat = Jsonx.formatCaseClass[ListOuter]
  }

  case class ClassOuter(outer: List[ListOuter])

  object ClassOuter {
    implicit def jsonFormat = Jsonx.formatCaseClass[ClassOuter]
  }

  case class ListInner2(string: String)

  object ListInner2 {
    implicit def jsonFormat = Jsonx.formatCaseClass[ListInner2]
  }

  case class ListOuter2(inner: List[ListInner2])

  object ListOuter2 {
    implicit def jsonFormat = Jsonx.formatCaseClass[ListOuter2]
  }

  case class ClassOuter2(outer: List[ListOuter2])

  object ClassOuter2 {
    implicit def jsonFormat = Jsonx.formatCaseClass[ClassOuter2]
  }

}

