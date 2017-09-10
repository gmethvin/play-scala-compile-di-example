
import akka.stream.Materializer
import play.api.http._
import play.api.libs.crypto.CSRFTokenSigner
import play.api.libs.typedmap.TypedKey
import play.api.mvc._
import play.api.routing.Router
import play.api.{controllers => _, _}
import play.filters.HttpFiltersComponents

class MyApplicationLoader extends ApplicationLoader {
  private var components: MyComponents = _

  def load(context: ApplicationLoader.Context): Application = {
    components = new MyComponents(context)
    components.application
  }
}

class MyComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
  with controllers.AssetsComponents {

  private val RequestScopeAttr: TypedKey[RequestScope] = TypedKey("requestScope")
  implicit class RequestScopeImplicits(val request: RequestHeader) {
    def withNewScope: RequestHeader = request.addAttr(RequestScopeAttr, new RequestScope(request))
    def scope: RequestScope = request.attrs.get(RequestScopeAttr)
      .getOrElse(throw new IllegalStateException("No RequestScope has been set for this request!"))
  }

  class RequestScope(val request: RequestHeader) extends RequestScopeMethodProxy(this) with HttpFiltersComponents {
    lazy val homeController = new controllers.HomeController(request, controllerComponents)

    lazy val router: Router = new _root_.router.Routes(httpErrorHandler, homeController, assets)
    lazy val httpErrorHandler: HttpErrorHandler =
      new DefaultHttpErrorHandler(environment, configuration, sourceMapper, Some(router))
    lazy val httpRequestHandler =
      new DefaultHttpRequestHandler(router, httpErrorHandler, httpConfiguration, httpFilters: _*)
  }

  override lazy val httpRequestHandler: HttpRequestHandler = (request: RequestHeader) => {
    val scopedRequest = request.withNewScope
    scopedRequest.scope.httpRequestHandler.handlerForRequest(scopedRequest)
  }

  // This HttpErrorHandler is used by the server in case the httpRequestHandler throws an exception
  override lazy val httpErrorHandler: HttpErrorHandler =
    new DefaultHttpErrorHandler(environment, configuration, sourceMapper, router = None)

  // router is only used by HttpErrorHandler in global scope, which is redefined above
  override lazy val router: Router = movedToRequestScope
  // httpFilters are only used by httpRequestHandler, which is only used in the request scope
  override lazy val httpFilters: Seq[EssentialFilter] = movedToRequestScope

  @inline private def movedToRequestScope: Nothing = throw new UnsupportedOperationException(
    "This method is not meant to be used. Use the method in request scope."
  )
}

class RequestScopeMethodProxy(delegate: MyComponents) {
  // These methods allow accessing outer methods when needed by a trait in the request scope
  def configuration: Configuration = delegate.configuration
  def csrfTokenSigner: CSRFTokenSigner = delegate.csrfTokenSigner
  def httpConfiguration: HttpConfiguration = delegate.httpConfiguration
  implicit def materializer: Materializer = delegate.materializer
}
