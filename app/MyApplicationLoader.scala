
import io.methvin.autodelegate.AutoDelegate
import play.api.http._
import play.api.libs.typedmap.TypedKey
import play.api.mvc._
import play.api.routing.Router
import play.api.{controllers => _, _}
import play.core.SourceMapper
import play.filters.HttpFiltersComponents

class MyApplicationLoader extends ApplicationLoader {
  def load(context: ApplicationLoader.Context): Application = new MyComponents(context).application
}

class MyComponents(context: ApplicationLoader.Context) extends BuiltInComponentsFromContext(context) { components =>

  override lazy val httpRequestHandler: HttpRequestHandler = (rh: RequestHeader) => {
    // Use the AutoDelegate macro to delegate any abstract methods in RequestComponents to MyComponents
    trait RequestComponentsWithRequest extends RequestComponents { val request: RequestHeader = rh }
    val requestComponents = AutoDelegate[RequestComponentsWithRequest](components)
    val scopedRequest = rh.addAttr(RequestComponents.Attr, requestComponents)
    requestComponents.httpRequestHandler.handlerForRequest(scopedRequest)
  }

  // This HttpErrorHandler is used by the server in case the httpRequestHandler throws an exception
  override lazy val httpErrorHandler: HttpErrorHandler =
    new DefaultHttpErrorHandler(environment, configuration, sourceMapper, router = None)

  // router is only used by HttpErrorHandler in global scope, which is redefined in the request scope
  override lazy val router: Router = movedToRequestScope
  // httpFilters are only used by httpRequestHandler, which is only used in the request scope
  override lazy val httpFilters: Seq[EssentialFilter] = movedToRequestScope

  @inline private def movedToRequestScope: Nothing = throw new UnsupportedOperationException(
    "This method is not meant to be used. Use the method on RequestComponents."
  )
}

// This should extend any traits that need to be in the request scope
trait RequestComponents extends HttpFiltersComponents with controllers.AssetsComponents {
  def request: RequestHeader

  def controllerComponents: ControllerComponents
  def sourceMapper: Option[SourceMapper]

  lazy val homeController =
    new controllers.HomeController(request, controllerComponents)
  lazy val router: Router =
    new _root_.router.Routes(httpErrorHandler, homeController, assets)
  lazy val httpErrorHandler: HttpErrorHandler =
    new DefaultHttpErrorHandler(environment, configuration, sourceMapper, Some(router))
  lazy val httpRequestHandler =
    new DefaultHttpRequestHandler(router, httpErrorHandler, httpConfiguration, httpFilters: _*)
}

object RequestComponents {
  // This attribute can be used by components in the request scope if needed
  final val Attr: TypedKey[RequestComponents] = TypedKey("RequestComponents")
}
