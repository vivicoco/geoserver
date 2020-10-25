/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.system.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.Sensors;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

/**
 * Retrieve real system information metrics defined in {@link MetricInfo} using OSHI library
 *
 * <p>Is possible to extends this class to change or add others low level APIs and use this new
 * class as main collector after register using autowiring.
 *
 * @author sandr
 * @see <a href="https://github.com/oshi/oshi">OSHI library </a>
 */
@Component
public class OSHISystemInfoCollector extends BaseSystemInfoCollector {

    private static final long serialVersionUID = 502867203324474735L;

    private static Log log = LogFactory.getLog(OSHISystemInfoCollector.class);

    private OperatingSystem os;

    private HardwareAbstractionLayer hal;

    private CentralProcessor pr;

    private GlobalMemory mm;

    private Sensors ss;

    private FileSystem fs;

    private volatile double cpuUsage = 0;

    public OSHISystemInfoCollector() {
        SystemInfo si = new SystemInfo();
        os = si.getOperatingSystem();
        hal = si.getHardware();
        pr = hal.getProcessor();
        mm = hal.getMemory();
        ss = hal.getSensors();
        fs = os.getFileSystem();
        // compute CPU usage for this process
        new Thread(
                        () -> {
                            boolean processExists = true;
                            long currentTime = 0;
                            long previousTime = 0;
                            long timeDifference = 0;
                            while (processExists) {
                                OSProcess process = os.getProcess(os.getProcessId());
                                if (process != null) {
                                    currentTime = process.getKernelTime() + process.getUserTime();
                                    if (previousTime != -1) {
                                        timeDifference = currentTime - previousTime;
                                        int processors = pr.getLogicalProcessorCount();
                                        if (processors > 0) {
                                            cpuUsage =
                                                    (100d * (timeDifference / ((double) 1000)))
                                                            / pr.getLogicalProcessorCount();
                                        } else {
                                            cpuUsage = -1;
                                        }
                                    }
                                    previousTime = currentTime;
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new RuntimeException(e);
                                    }
                                } else {
                                    processExists = false;
                                }
                            }
                        })
                .start();
    }

    @Override
    List<MetricValue> retrieveSystemInfo(MetricInfo info) {
        List<MetricValue> si = super.retrieveSystemInfo(info);
        try {
            switch (info) {
                    // system metrics
                case OPERATING_SYSTEM:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(os.getFamily() + " " + os.getVersionInfo().getVersion());
                        si = Collections.singletonList(mv);
                        break;
                    }
                case SYSTEM_UPTIME:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(os.getSystemUptime());
                        si = Collections.singletonList(mv);
                        break;
                    }
                case SYSTEM_AVERAGE_LOAD_1:
                    {
                        MetricValue mv = new MetricValue(info);
                        double lv = pr.getSystemLoadAverage(1)[0];
                        if (lv > 0) {
                            mv.setAvailable(true);
                            mv.setValue(lv);
                            si = Collections.singletonList(mv);
                        }
                        break;
                    }
                case SYSTEM_AVERAGE_LOAD_5:
                    {
                        MetricValue mv = new MetricValue(info);
                        double lv = pr.getSystemLoadAverage(2)[1];
                        if (lv > 0) {
                            mv.setAvailable(true);
                            mv.setValue(lv);
                            si = Collections.singletonList(mv);
                        }
                        break;
                    }
                case SYSTEM_AVERAGE_LOAD_15:
                    {
                        MetricValue mv = new MetricValue(info);
                        double lv = pr.getSystemLoadAverage(3)[2];
                        if (lv > 0) {
                            mv.setAvailable(true);
                            mv.setValue(lv);
                            si = Collections.singletonList(mv);
                        }
                        break;
                    }
                    // cpu metrics
                case PHYSICAL_CPUS:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(pr.getPhysicalProcessorCount());
                        si = Collections.singletonList(mv);
                        break;
                    }
                case LOGICAL_CPUS:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(pr.getLogicalProcessorCount());
                        si = Collections.singletonList(mv);
                        break;
                    }
                case RUNNING_PROCESS:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(os.getProcessCount());
                        si = Collections.singletonList(mv);
                        break;
                    }
                case RUNNING_THREADS:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(os.getThreadCount());
                        si = Collections.singletonList(mv);
                        break;
                    }
                    // This has been removed from the OSHI API, one could get this from an ORACLE
                    // JVM, but not otherwise
                    //                case CPU_LOAD:
                    //                    {
                    //                        MetricValue mv = new MetricValue(info);
                    //                        double cpuLoad = pr.getSystemCpuLoad();
                    //                        if (cpuLoad >= 0) {
                    //                            mv.setAvailable(true);
                    //                            mv.setValue(cpuLoad * 100.0);
                    //                            mv.setDescription("CPU load average");
                    //                            si = Collections.singletonList(mv);
                    //                        }
                    //                        break;
                    //                    }
                case PER_CPU_LOAD:
                    {
                        si = new ArrayList<>();
                        double[] loads = pr.getProcessorCpuLoadBetweenTicks();
                        if (loads.length > 0) {
                            for (int i = 0; i < loads.length; i++) {
                                double value = loads[i] * 100.0;
                                String description = "CPU " + (i + 1) + " load";
                                MetricValue mv = new MetricValue(info);
                                mv.setValue(value);
                                mv.setAvailable(true);
                                mv.setDescription(description);
                                mv.setPriority(info.getPriority() + i);
                                mv.setIdentifier("CPU " + (i + 1));
                                si.add(mv);
                            }
                        }
                        break;
                    }
                    // memory metrics
                case MEMORY_USED:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        double total = mm.getTotal();
                        if (total > 0.0) {
                            double used = total - mm.getAvailable();
                            mv.setValue((used / total) * 100);
                        } else {
                            mv.setValue(0);
                        }
                        si = Collections.singletonList(mv);
                        break;
                    }
                case MEMORY_TOTAL:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(mm.getTotal());
                        si = Collections.singletonList(mv);
                        break;
                    }
                case MEMORY_FREE:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(mm.getAvailable());
                        si = Collections.singletonList(mv);
                        break;
                    }
                    // swap metrics
                case SWAP_USED:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        double total = mm.getVirtualMemory().getSwapTotal();
                        if (total > 0.0) {
                            double used = mm.getVirtualMemory().getSwapUsed();
                            mv.setValue(used / total * 100);
                        } else {
                            mv.setValue(0);
                        }
                        si = Collections.singletonList(mv);
                        break;
                    }
                case SWAP_TOTAL:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(mm.getVirtualMemory().getSwapTotal());
                        si = Collections.singletonList(mv);
                        break;
                    }
                case SWAP_FREE:
                    {
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        long total = mm.getVirtualMemory().getSwapTotal();
                        long free = total - mm.getVirtualMemory().getSwapUsed();
                        mv.setValue(free);
                        si = Collections.singletonList(mv);
                        break;
                    }
                    // file system metrics
                case FILE_SYSTEM_TOTAL_USAGE:
                    {
                        si = Collections.emptyList();
                        List<OSFileStore> fss = fs.getFileStores();
                        if (!fss.isEmpty()) {
                            double total = 0;
                            double used = 0;
                            for (OSFileStore fs : fss) {
                                double fsTotal = fs.getTotalSpace();
                                total += fsTotal;
                                used += fsTotal - fs.getUsableSpace();
                            }
                            MetricValue mv = new MetricValue(info);
                            if (total > 0.0) {
                                mv.setValue(used / total * 100);
                            } else {
                                mv.setValue(0);
                            }
                            mv.setAvailable(true);
                            si = Collections.singletonList(mv);
                        }
                        break;
                    }
                case PARTITION_USED:
                    {
                        List<OSFileStore> fss = fs.getFileStores();
                        if (!fss.isEmpty()) {
                            si = new ArrayList<>();
                            int i = 0;
                            for (OSFileStore fs : fss) {
                                MetricValue mv = new MetricValue(info);
                                double total = fs.getTotalSpace();
                                if (total > 0.0) {
                                    double used = total - fs.getUsableSpace();
                                    mv.setValue(used / total * 100);
                                } else {
                                    mv.setValue(0);
                                }
                                mv.setAvailable(true);
                                mv.setDescription("Partition [" + fs.getName() + "] used space");
                                mv.setPriority(info.getPriority() + (i + 1) * 3);
                                mv.setIdentifier(fs.getName());
                                si.add(mv);
                            }
                        }
                        break;
                    }
                case PARTITION_TOTAL:
                    {
                        List<OSFileStore> fss = fs.getFileStores();
                        if (!fss.isEmpty()) {
                            si = new ArrayList<>();
                            int i = 0;
                            for (OSFileStore fs : fss) {
                                MetricValue mv = new MetricValue(info);
                                mv.setValue(fs.getTotalSpace());
                                mv.setAvailable(true);
                                mv.setDescription("Partition [" + fs.getName() + "] total space");
                                mv.setPriority(info.getPriority() + (i + 1) * 3);
                                mv.setIdentifier(fs.getName());
                                si.add(mv);
                            }
                        }
                        break;
                    }
                case PARTITION_FREE:
                    {
                        List<OSFileStore> fss = fs.getFileStores();
                        if (!fss.isEmpty()) {
                            si = new ArrayList<>();
                            int i = 0;
                            for (OSFileStore fs : fss) {
                                MetricValue mv = new MetricValue(info);
                                mv.setValue(fs.getUsableSpace());
                                mv.setAvailable(true);
                                mv.setDescription("Partition [" + fs.getName() + "] free space");
                                mv.setPriority(info.getPriority() + (i + 1) * 3);
                                mv.setIdentifier(fs.getName());
                                si.add(mv);
                            }
                        }
                        break;
                    }
                    // network metrics
                case NETWORK_INTERFACES_SEND:
                    {
                        si = Collections.emptyList();
                        List<NetworkIF> nis = hal.getNetworkIFs();
                        if (!nis.isEmpty()) {
                            double total = 0;
                            for (NetworkIF ni : nis) {
                                ni.updateAttributes();
                                total += ni.getBytesSent();
                            }
                            MetricValue mv = new MetricValue(info);
                            mv.setValue(total);
                            mv.setAvailable(true);
                            si = Collections.singletonList(mv);
                        }
                        break;
                    }
                case NETWORK_INTERFACES_RECEIVED:
                    {
                        si = Collections.emptyList();
                        List<NetworkIF> nis = hal.getNetworkIFs();
                        if (!nis.isEmpty()) {
                            double total = 0;
                            for (NetworkIF ni : nis) {
                                ni.updateAttributes();
                                total += ni.getBytesRecv();
                            }
                            MetricValue mv = new MetricValue(info);
                            mv.setValue(total);
                            mv.setAvailable(true);
                            si = Collections.singletonList(mv);
                        }
                        break;
                    }
                case NETWORK_INTERFACE_SEND:
                    {
                        List<NetworkIF> nis = hal.getNetworkIFs();
                        if (!nis.isEmpty()) {
                            si = new ArrayList<>();
                            int i = 0;
                            for (NetworkIF ni : nis) {
                                ni.updateAttributes();
                                MetricValue mv = new MetricValue(info);
                                mv.setValue(ni.getBytesSent());
                                mv.setAvailable(true);
                                mv.setDescription("Network interface [" + ni.getName() + "] send");
                                mv.setPriority(info.getPriority() + (i + 1) * 3);
                                mv.setIdentifier(ni.getName());
                                si.add(mv);
                            }
                        }
                        break;
                    }
                case NETWORK_INTERFACE_RECEIVED:
                    {
                        List<NetworkIF> nis = hal.getNetworkIFs();
                        if (!nis.isEmpty()) {
                            si = new ArrayList<>();
                            int i = 0;
                            for (NetworkIF ni : nis) {
                                ni.updateAttributes();
                                MetricValue mv = new MetricValue(info);
                                mv.setValue(ni.getBytesRecv());
                                mv.setAvailable(true);
                                mv.setDescription(
                                        "Network interface [" + ni.getName() + "] received");
                                mv.setPriority(info.getPriority() + (i++) * 3);
                                mv.setIdentifier(ni.getName());
                                si.add(mv);
                            }
                        }
                        break;
                    }
                    // sensors metrics
                case TEMPERATURE:
                    {
                        double value = ss.getCpuTemperature();
                        if (value > 0) {
                            MetricValue mv = new MetricValue(info);
                            mv.setAvailable(true);
                            mv.setValue(value);
                            si = Collections.singletonList(mv);
                        }
                        break;
                    }
                case VOLTAGE:
                    {
                        double value = ss.getCpuVoltage();
                        if (value > 0) {
                            MetricValue mv = new MetricValue(info);
                            mv.setAvailable(true);
                            mv.setValue(value);
                            si = Collections.singletonList(mv);
                        }
                        break;
                    }
                case FAN_SPEED:
                    {
                        int[] speeds = ss.getFanSpeeds();
                        if (speeds.length > 0) {
                            ArrayList<MetricValue> tmp = new ArrayList<>(speeds.length);
                            for (int i = 0; i < speeds.length; i++) {
                                if (speeds[i] > 0) {
                                    int value = speeds[i];
                                    String name = info.name() + "_" + (i + 1);
                                    String description = "Speed fan " + (i + 1);
                                    MetricValue mv = new MetricValue(info);
                                    mv.setValue(value);
                                    mv.setAvailable(true);
                                    mv.setDescription(description);
                                    mv.setName(name);
                                    tmp.add(mv);
                                }
                            }
                            if (!tmp.isEmpty()) {
                                si = tmp;
                            }
                        }
                        break;
                    }
                    // geoserver metrics
                case GEOSERVER_CPU_USAGE:
                    {
                        if (cpuUsage >= 0.0) {
                            MetricValue mv = new MetricValue(info);
                            mv.setAvailable(true);
                            mv.setValue(cpuUsage);
                            si = Collections.singletonList(mv);
                        }
                        break;
                    }
                case GEOSERVER_THREADS:
                    {
                        OSProcess gsProc = os.getProcess(os.getProcessId());
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        mv.setValue(gsProc.getThreadCount());
                        si = Collections.singletonList(mv);
                        break;
                    }
                case GEOSERVER_JVM_MEMORY_USAGE:
                    {
                        OSProcess gsProc = os.getProcess(os.getProcessId());
                        MetricValue mv = new MetricValue(info);
                        mv.setAvailable(true);
                        double total = mm.getTotal();
                        if (total > 0.0) {
                            double value = 100d * gsProc.getResidentSetSize() / total;
                            mv.setValue(value);
                        } else {
                            mv.setValue(0);
                        }
                        si = Collections.singletonList(mv);
                        break;
                    }
                default:
                    break;
            }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
        return si;
    }
}
