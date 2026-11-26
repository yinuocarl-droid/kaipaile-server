using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

internal static class FakeJava
{
    private static int Main(string[] args)
    {
        try
        {
            if (HasArgument(args, "-version"))
            {
                Console.Error.WriteLine("openjdk version \"17.0.0\"");
                return 0;
            }

            int port = ReadPort(args);
            string mode = Environment.GetEnvironmentVariable("KAIPAI_LAUNCHER_TEST_MODE") ?? "listen";
            string markerPath = Environment.GetEnvironmentVariable("KAIPAI_LAUNCHER_TEST_MARKER");
            int delayMilliseconds = ReadNonNegativeInteger(
                Environment.GetEnvironmentVariable("KAIPAI_LAUNCHER_TEST_DELAY_MS"));

            if (port <= 0 || String.IsNullOrWhiteSpace(markerPath))
            {
                return 64;
            }

            WriteMarker(markerPath, port, false);
            if (String.Equals(mode, "timeout", StringComparison.OrdinalIgnoreCase))
            {
                Thread.Sleep(TimeSpan.FromMinutes(2));
                return 0;
            }

            if (delayMilliseconds > 0)
            {
                Thread.Sleep(delayMilliseconds);
            }

            TcpListener listener = new TcpListener(IPAddress.Loopback, port);
            listener.Start();
            WriteMarker(markerPath, port, true);

            while (true)
            {
                using (TcpClient client = listener.AcceptTcpClient())
                {
                    RespondToReadinessRequest(client);
                }
            }
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine("FakeJava failed: " + exception.GetType().FullName);
            return 70;
        }
    }

    private static bool HasArgument(string[] args, string expected)
    {
        foreach (string argument in args)
        {
            if (String.Equals(argument, expected, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }
        return false;
    }

    private static int ReadPort(string[] args)
    {
        const string prefix = "--server.port=";
        for (int index = 0; index < args.Length; index++)
        {
            string argument = args[index] ?? String.Empty;
            if (argument.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
            {
                int inlinePort;
                return Int32.TryParse(argument.Substring(prefix.Length), out inlinePort) ? inlinePort : -1;
            }

            if (String.Equals(argument, "--server.port", StringComparison.OrdinalIgnoreCase) &&
                index + 1 < args.Length)
            {
                int separatePort;
                return Int32.TryParse(args[index + 1], out separatePort) ? separatePort : -1;
            }
        }
        return -1;
    }

    private static int ReadNonNegativeInteger(string value)
    {
        int parsed;
        return Int32.TryParse(value, out parsed) && parsed > 0 ? parsed : 0;
    }

    private static void RespondToReadinessRequest(TcpClient client)
    {
        NetworkStream stream = client.GetStream();
        stream.ReadTimeout = 2000;
        byte[] requestBuffer = new byte[4096];
        int totalRead = 0;
        while (totalRead < requestBuffer.Length)
        {
            int read = stream.Read(requestBuffer, totalRead, requestBuffer.Length - totalRead);
            if (read <= 0)
            {
                break;
            }
            totalRead += read;
            string requestText = Encoding.ASCII.GetString(requestBuffer, 0, totalRead);
            if (requestText.IndexOf("\r\n\r\n", StringComparison.Ordinal) >= 0)
            {
                break;
            }
        }

        const string body =
            "{\"configUrl\":\"/api/v3/api-docs/swagger-config\",\"url\":\"/api/v3/api-docs\"}";
        byte[] bodyBytes = Encoding.UTF8.GetBytes(body);
        string headers =
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: " + bodyBytes.Length + "\r\n" +
            "Connection: close\r\n\r\n";
        byte[] headerBytes = Encoding.ASCII.GetBytes(headers);
        stream.Write(headerBytes, 0, headerBytes.Length);
        stream.Write(bodyBytes, 0, bodyBytes.Length);
        stream.Flush();
    }

    private static void WriteMarker(string markerPath, int port, bool ready)
    {
        int processId = Process.GetCurrentProcess().Id;
        string temporaryPath = markerPath + "." + processId + ".tmp";
        string[] lines =
        {
            "PID=" + processId,
            "PORT=" + port,
            "READY=" + (ready ? "1" : "0"),
            "APP_ID_PRESENT=" + PresenceFlag("WECHAT_MINIAPP_APP_ID"),
            "APP_SECRET_PRESENT=" + PresenceFlag("WECHAT_MINIAPP_APP_SECRET")
        };

        File.WriteAllLines(temporaryPath, lines);
        if (File.Exists(markerPath))
        {
            File.Delete(markerPath);
        }
        File.Move(temporaryPath, markerPath);
    }

    private static string PresenceFlag(string key)
    {
        return String.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable(key)) ? "0" : "1";
    }
}
