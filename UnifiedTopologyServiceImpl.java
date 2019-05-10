package com.lumina.pkg.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPoint;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.http.luminanetworks.com.lsc.ext.base.rev160413.ModuleName;
import org.opendaylight.yang.gen.v1.http.luminanetworks.com.lsc.ext.device.database.rev160608.Devices;
import org.opendaylight.yang.gen.v1.http.luminanetworks.com.lsc.ext.device.database.rev160608.DeviceArgs.DeviceType;
import org.opendaylight.yang.gen.v1.http.luminanetworks.com.lsc.ext.device.database.rev160608.devices.Device;
import org.opendaylight.yang.gen.v1.http.luminanetworks.com.lsc.ext.device.database.rev160608.devices.DeviceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.jsonrpc.rev161201.Config;
import org.opendaylight.yang.gen.v1.urn.opendaylight.jsonrpc.rev161201.config.ConfiguredEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.jsonrpc.rev161201.config.ConfiguredEndpointsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.GetTopologyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.GetTopologyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.GetTopologyOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.UnifiedInventoryService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.unified.topology.UnifiedNetworkTopology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.unified.topology.UnifiedNetworkTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.unified.topology.unified.network.topology.UnifiedTopology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.unified.topology.unified.network.topology.UnifiedTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.unified.topology.unified.network.topology.unified.topology.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.unified.inventory.rev190404.unified.topology.unified.network.topology.unified.topology.NodesBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

@SuppressWarnings("deprecation")
public class UnifiedTopologyServiceImpl implements UnifiedInventoryService {

	private static final Logger LOG = LoggerFactory.getLogger(UnifiedTopologyServiceImpl.class);

	private DataBroker dataBroker;
	private MountPointService mountPointService;

	/*private static final InstanceIdentifier<ConfiguredEndpoints> DEVICEDB_IID = InstanceIdentifier.create(Config.class)
			.child(ConfiguredEndpoints.class, new ConfiguredEndpointsKey("devicedb"));*/

	LoadingCache<String, KeyedInstanceIdentifier<ConfiguredEndpoints, ConfiguredEndpointsKey>> mountIds = CacheBuilder
			.newBuilder().maximumSize(20)
			.build(new CacheLoader<String, KeyedInstanceIdentifier<ConfiguredEndpoints, ConfiguredEndpointsKey>>() {

				@Override
				public KeyedInstanceIdentifier<ConfiguredEndpoints, ConfiguredEndpointsKey> load(final String key) {
					InstanceIdentifier<Config> configIid = InstanceIdentifier.create(Config.class);
					return configIid.child(ConfiguredEndpoints.class, new ConfiguredEndpointsKey(key));
				}
			});

	public UnifiedTopologyServiceImpl(final DataBroker dataBroker,
			final MountPointService mountPointService) {
		this.dataBroker = dataBroker;
		this.mountPointService = mountPointService;
	}

	public DataBroker getDataBroker() {
		return dataBroker;
	}

	public void setDataBroker(DataBroker dataBroker) {
		this.dataBroker = dataBroker;
	}

	public MountPointService getMountPointService() {
		return mountPointService;
	}

	public void setMountPointService(MountPointService mountPointService) {
		this.mountPointService = mountPointService;
	}

	/**
	 * Method called when the blueprint container is created.
	 */
	public void init() {
		LOG.info("UnifiedTopologyImpl Session Initiated");
	}

	/**
	 * Method called when the blueprint container is destroyed.
	 */
	public void close() {
		LOG.info("UnifiedTopologyImpl Closed");
	}

	public void lscDevicesInfo(GetTopologyInput input, InstanceIdentifier<NetworkTopology> topoIid, List<UnifiedTopology> unifiedTopoList) {
		final ReadWriteTransaction readTx = dataBroker.newReadWriteTransaction();
		final ListenableFuture<Optional<NetworkTopology>> readTopoFuture = readTx.read(LogicalDatastoreType.OPERATIONAL,
				topoIid);

		Futures.addCallback(readTopoFuture, new FutureCallback<Optional<NetworkTopology>>() {
			@Override
			public void onSuccess(Optional<NetworkTopology> networkTopologyOpt) {
				if (networkTopologyOpt.isPresent()) {
					NetworkTopology netTopo = networkTopologyOpt.get();

					LOG.error("Net Topology is {}", netTopo);
					List<Topology> topoList = netTopo.getTopology();
					for (Topology topology : topoList) {
						LOG.info("Topology: {}", topology);
						TopologyId topoId = topology.getTopologyId();
						List<Nodes> unifiedNodesList = new ArrayList<Nodes>();
						try {
							if(topology.getNode() != null || !topology.getNode().isEmpty()) {
								List<Node> nodeList = topology.getNode();
								LOG.info("Nodelist:{}", nodeList);
								for (Node node : nodeList) {
									NodeId nodeId = node.getNodeId();
									if (topoId.getValue().equals("topology-netconf")) {
										InstanceIdentifier<Node> nodeRef = InstanceIdentifier.create(NetworkTopology.class)
												.child(Topology.class, new TopologyKey(topoId))
												.child(Node.class, new NodeKey(nodeId));

										unifiedNodesList.add(new NodesBuilder().setNodeId(nodeId.getValue())
												.setNodeReference(nodeRef).build());
									} else if (topoId.getValue().equals("flow:1")) {
										unifiedNodesList.add(new NodesBuilder().setNodeId(nodeId.getValue())
												.setNodeReference(new InventoryReference().getOpenflowIid(nodeId.getValue()))
												.build());
									}
								}
							}
						}
						catch (NullPointerException e) {
							LOG.error("Null pointer exception: {}",e.getMessage());
							continue;
						}

						unifiedTopoList.add(new UnifiedTopologyBuilder()
								.setTopologyId(topoId.getValue())
								.setNodes(unifiedNodesList).build());
					}
				}
			}

			@Override
			public void onFailure(Throwable t) {
				LOG.error("Exception has occured: {}", t);

			}
		}, MoreExecutors.directExecutor());

	}
	public void leapDevicesInfo(GetTopologyInput input, InstanceIdentifier<NetworkTopology> topoIid, List<UnifiedTopology> unifiedTopoList) {
		MountPoint deviceDbMount = getDeviceDbMonunt();
		LOG.info("Device db mount: {}, {}", deviceDbMount, deviceDbMount.getIdentifier());
		try {
			DataBroker db = deviceDbMount.getService(DataBroker.class).get();
			InstanceIdentifier<Devices> deviceDbIid = InstanceIdentifier.create(Devices.class);
			ReadWriteTransaction deviceDbReadTx = db.newReadWriteTransaction();

			final CheckedFuture<Optional<Devices>, ReadFailedException> readDevicedbFuture = deviceDbReadTx.read(LogicalDatastoreType.CONFIGURATION,
					deviceDbIid);	
			Futures.addCallback(readDevicedbFuture, new FutureCallback<Optional<Devices>>() {

				@Override
				public void onSuccess(Optional<Devices> devicesOpt) {
					if (devicesOpt.isPresent() ) {
						LOG.info("{}",devicesOpt.get());

						List<Nodes> unifiedNodesList = new ArrayList<Nodes>();
						Devices devices = devicesOpt.get();

						List<Device> deviceList = devices.getDevice();
						for (Device device : deviceList) {
							String nodeId = device.getEntity();
							IpAddress nodeIp = device.getAddress();
							List<ModuleName> modules = device.getModules();
							DeviceType nodeType = device.getDeviceType();

							InstanceIdentifier<Device> deviceIid = InstanceIdentifier.create(Devices.class)
									.child(Device.class, new DeviceKey(nodeId));
							unifiedNodesList.add(new NodesBuilder()
									.setNodeId(nodeId)
									.setNodeReference(deviceIid)
									.setAddress(nodeIp)
									.setModules(modules)
									.setDeviceType(nodeType)
									.build());
						}

						unifiedTopoList.add(new UnifiedTopologyBuilder()
								.setTopologyId("leap-topology")
								.setNodes(unifiedNodesList).build());
					}
				}

				@Override
				public void onFailure(Throwable t) {

				}
			}, MoreExecutors.directExecutor());
		} catch(NullPointerException e) {
			LOG.error("Null pointer exception:{}", e);
		}
	}

	@Override
	public ListenableFuture<RpcResult<GetTopologyOutput>> getTopology(GetTopologyInput input) {

		InstanceIdentifier<NetworkTopology> topoIid = InstanceIdentifier.create(NetworkTopology.class);

		List<UnifiedTopology> unifiedTopoList = new ArrayList<UnifiedTopology>();
		if (input.getTopologyId().name().equals("LSC") | input.getTopologyId().name().equals("ALL") ) {
			lscDevicesInfo(input, topoIid, unifiedTopoList);
		}else if (input.getTopologyId().name().equals("LEAP") | input.getTopologyId().name().equals("ALL") ) {
			leapDevicesInfo(input, topoIid, unifiedTopoList);
		}

		UnifiedNetworkTopology unifiedNetTopo = new UnifiedNetworkTopologyBuilder()
				.setUnifiedTopology(unifiedTopoList).build();

		GetTopologyOutput output = new GetTopologyOutputBuilder().setUnifiedNetworkTopology(unifiedNetTopo).build();
		final SettableFuture<RpcResult<GetTopologyOutput>> returnFuture = SettableFuture.create();
		returnFuture.set(RpcResultBuilder.success(output).build());

		LOG.info("{}", returnFuture);

		return returnFuture;
	}


	private MountPoint getDeviceDbMonunt() {
		Optional<MountPoint> deviceDbOptional;
		try {
			deviceDbOptional = mountPointService.getMountPoint(mountIds.get("devicedb"));
			if (deviceDbOptional.isPresent()) {
				MountPoint deviceDbMount = deviceDbOptional.get();
				LOG.warn("Dev db mountpoint: {}", deviceDbMount);
				return deviceDbMount;
			} else {
				return null;
			}
		} catch (ExecutionException e) {
			e.printStackTrace();
			return null;
		}
	}
}
