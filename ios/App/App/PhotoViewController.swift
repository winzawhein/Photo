import UIKit
import WebKit
import Capacitor

// Remote content uses its own WebView and never receives the Capacitor bridge.
final class PhotoViewController: CAPBridgeViewController, WKNavigationDelegate, WKScriptMessageHandler {
    private let home = URL(string: "https://antiquewhite-hornet-229089.hostingersite.com/")!
    private var site: WKWebView!
    private let spinner = UIActivityIndicatorView(style: .medium)
    private let retry = UIButton(type: .system)
    private var timeout: DispatchWorkItem?

    override func viewDidLoad() {
        super.viewDidLoad()
        // Capacitor's loadView makes its WebView the root view. Replace that
        // root with a visible container before adding the isolated website view.
        webView?.isHidden = true
        view = UIView(frame: .zero)
        view.backgroundColor = UIColor(red: 0.953, green: 0.984, blue: 0.992, alpha: 1)
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.ignoresViewportScaleLimits = false
        let controller = config.userContentController
        controller.add(WeakPhotoHandler(self), name: "photoRefresh")
        let guardSource = "if (location.origin === 'https://antiquewhite-hornet-229089.hostingersite.com') {"
        let adapter = "window.PhotoRefresh = { postMessage: function(value) { window.webkit.messageHandlers.photoRefresh.postMessage(value); } };"
        controller.addUserScript(WKUserScript(source: guardSource + adapter + "}", injectionTime: .atDocumentStart, forMainFrameOnly: true))
        for name in ["mobile-layout", "ios-viewport", "photo-zoom", "pull-refresh"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "js", subdirectory: "MobileAssets"),
               let source = try? String(contentsOf: url, encoding: .utf8) {
                controller.addUserScript(WKUserScript(source: guardSource + source + "}", injectionTime: .atDocumentEnd, forMainFrameOnly: true))
            }
        }
        site = WKWebView(frame: .zero, configuration: config)
        site.navigationDelegate = self
        site.allowsBackForwardNavigationGestures = true
        site.scrollView.contentInsetAdjustmentBehavior = .never
        site.scrollView.pinchGestureRecognizer?.isEnabled = false
        site.isOpaque = false
        site.backgroundColor = view.backgroundColor
        site.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(site)
        NSLayoutConstraint.activate([
            site.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            site.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            site.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            site.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor)
        ])
        spinner.translatesAutoresizingMaskIntoConstraints = false
        spinner.hidesWhenStopped = true
        spinner.color = .systemTeal
        view.addSubview(spinner)
        retry.setTitle("Unable to load Photo. Tap to retry", for: .normal)
        retry.backgroundColor = .systemBackground
        retry.layer.cornerRadius = 12
        retry.isHidden = true
        retry.translatesAutoresizingMaskIntoConstraints = false
        retry.addTarget(self, action: #selector(reloadPage), for: .touchUpInside)
        view.addSubview(retry)
        NSLayoutConstraint.activate([
            spinner.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            spinner.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            retry.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            retry.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            retry.widthAnchor.constraint(lessThanOrEqualTo: view.widthAnchor, constant: -32),
            retry.heightAnchor.constraint(greaterThanOrEqualToConstant: 48)
        ])
        site.load(URLRequest(url: home))
    }

    @objc private func reloadPage() {
        retry.isHidden = true
        if site.url == nil { site.load(URLRequest(url: home)) } else { site.reload() }
    }

    private func finishLoading() {
        timeout?.cancel()
        timeout = nil
        spinner.stopAnimating()
        site.evaluateJavaScript("window.__photoPullRefresh?.reset()", completionHandler: nil)
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.frameInfo.isMainFrame,
              message.frameInfo.securityOrigin.protocol == "https",
              message.frameInfo.securityOrigin.host == home.host,
              message.body as? String == "refresh" else { return }
        reloadPage()
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        timeout?.cancel()
        retry.isHidden = true
        spinner.startAnimating()
        let work = DispatchWorkItem { [weak self] in
            guard let self = self else { return }
            self.site.stopLoading()
            self.finishLoading()
            self.retry.isHidden = false
        }
        timeout = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 30, execute: work)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { finishLoading() }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { failed(error) }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { failed(error) }
    private func failed(_ error: Error) {
        if (error as NSError).code == NSURLErrorCancelled { return }
        finishLoading()
        retry.isHidden = false
    }
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        finishLoading()
        retry.isHidden = false
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else { decisionHandler(.cancel); return }
        if url.scheme == "https", url.host == home.host {
            decisionHandler(.allow)
            if navigationAction.targetFrame == nil { webView.load(navigationAction.request) }
        } else {
            decisionHandler(.cancel)
            if navigationAction.navigationType == .linkActivated,
               ["https", "http", "mailto", "tel"].contains(url.scheme ?? "") {
                UIApplication.shared.open(url)
            }
        }
    }
}

private final class WeakPhotoHandler: NSObject, WKScriptMessageHandler {
    weak var target: PhotoViewController?
    init(_ target: PhotoViewController) { self.target = target }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(userContentController, didReceive: message)
    }
}
