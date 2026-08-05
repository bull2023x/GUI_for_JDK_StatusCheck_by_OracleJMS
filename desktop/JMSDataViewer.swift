import Cocoa
import WebKit

private let appTitle = "JMS Data Viewer"
// Keep the packaged application separate from the 8080 development server.
private let serverURL = URL(string: "http://127.0.0.1:18180")!

final class AppDelegate: NSObject, NSApplicationDelegate, WKUIDelegate {
    private var window: NSWindow!
    private var webView: WKWebView?
    private var backend: Process?
    private let statusLabel = NSTextField(labelWithString: "Starting the local JMS service…")

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        makeWindow()
        startBackend()
        waitForBackend(attempt: 0)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

    func applicationWillTerminate(_ notification: Notification) {
        guard let backend, backend.isRunning else { return }
        backend.terminate()
    }

    private func makeWindow() {
        window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 1440, height: 920),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = appTitle
        window.center()
        window.minSize = NSSize(width: 980, height: 640)
        statusLabel.alignment = .center
        statusLabel.font = .systemFont(ofSize: 16)
        statusLabel.textColor = .secondaryLabelColor
        window.contentView = statusLabel
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    private func startBackend() {
        guard let jarURL = Bundle.main.url(forResource: "JavaFleetCommanderAI", withExtension: "jar") else {
            showFailure("The application bundle is incomplete. JavaFleetCommanderAI.jar is missing.")
            return
        }

        let logDirectory = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Logs/JMS Data Viewer", isDirectory: true)
        try? FileManager.default.createDirectory(at: logDirectory, withIntermediateDirectories: true)
        let logURL = logDirectory.appendingPathComponent("backend.log")
        FileManager.default.createFile(atPath: logURL.path, contents: nil)

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/java")
        process.arguments = ["-Dserver.address=127.0.0.1", "-Dserver.port=18180", "-jar", jarURL.path]
        process.standardOutput = try? FileHandle(forWritingTo: logURL)
        process.standardError = try? FileHandle(forWritingTo: logURL)
        process.terminationHandler = { [weak self] stoppedProcess in
            guard stoppedProcess.terminationStatus != 0 else { return }
            DispatchQueue.main.async {
                self?.showFailure("The local JMS service stopped unexpectedly.\n\nSee ~/Library/Logs/JMS Data Viewer/backend.log for details.")
            }
        }

        do {
            try process.run()
            backend = process
        } catch {
            showFailure("Java 17 or later is required to start this app.\n\n\(error.localizedDescription)")
        }
    }

    private func waitForBackend(attempt: Int) {
        guard backend?.isRunning == true else { return }
        URLSession.shared.dataTask(with: serverURL.appendingPathComponent("api/jms-data/status")) { [weak self] _, response, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                if (response as? HTTPURLResponse)?.statusCode == 200 {
                    self.showDashboard()
                } else if attempt < 120 {
                    self.statusLabel.stringValue = "Starting the local JMS service… (\(attempt + 1)s)"
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                        self.waitForBackend(attempt: attempt + 1)
                    }
                } else {
                    self.showFailure("The local JMS service did not start within two minutes.\n\nSee ~/Library/Logs/JMS Data Viewer/backend.log for details.")
                }
            }
        }.resume()
    }

    private func showDashboard() {
        let configuration = WKWebViewConfiguration()
        let view = WKWebView(frame: window.contentView?.bounds ?? .zero, configuration: configuration)
        view.autoresizingMask = [.width, .height]
        view.uiDelegate = self
        window.contentView = view
        webView = view
        view.load(URLRequest(url: serverURL))
    }

    private func showFailure(_ message: String) {
        statusLabel.stringValue = "Unable to start JMS Data Viewer"
        let alert = NSAlert()
        alert.messageText = "Unable to start JMS Data Viewer"
        alert.informativeText = message
        alert.alertStyle = .critical
        alert.addButton(withTitle: "Quit")
        alert.runModal()
        NSApp.terminate(nil)
    }
}

@main
struct JMSDataViewerApp {
    static func main() {
        let application = NSApplication.shared
        let delegate = AppDelegate()
        application.delegate = delegate
        application.setActivationPolicy(.regular)
        application.run()
    }
}
